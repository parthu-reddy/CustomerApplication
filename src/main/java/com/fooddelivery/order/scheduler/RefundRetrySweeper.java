package com.fooddelivery.order.scheduler;

import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;

@Component
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
/**
 * <strong>@replication-safe: distributed-lock</strong> -- guarded by a Redis lock before any refund is re-enqueued.
 *
 * <p>Classification recorded 2026-08-27 (Phase 7). Every @Scheduled class in this workspace
 * carries one of these markers; the BOOT-SCHEDULE-CLASSIFIED check fails on a new one that
 * does not. Change the marker only after re-reading what the job actually does.
 */
public class RefundRetrySweeper {
    

    private final IPaymentIntentRepository paymentIntentRepository;
    private final IOrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final com.fooddelivery.order.service.OrderRefundService orderRefundService;
    private final StringRedisTemplate redisTemplate;
    private final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.fooddelivery.order.repository.SupportTicketRepository supportTicketRepository;

    // Runs every 5 minutes
    /**
     * How long an intent may sit in REFUND_PENDING before it is treated as stuck. The outbox is
     * drained continuously, so a healthy refund settles in seconds; this is deliberately far longer
     * so a slow-but-working refund is never retried underneath itself.
     */
    static final long STUCK_REFUND_PENDING_MINUTES = 30;

    @Scheduled(fixedDelay = 300000)
    public void retryFailedRefunds() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(com.fooddelivery.common.constants.RedisKeyConstants.LOCK_SWEEP_REFUND_RETRIES, "1", java.time.Duration.ofSeconds(200));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime minTime = now.minusHours(48);
        org.springframework.data.domain.Page<PaymentIntent> failedIntentsPage = paymentIntentRepository.findByStatusAndCreatedAtBetween(PaymentIntentStatus.REFUND_FAILED, minTime, now, org.springframework.data.domain.PageRequest.of(0, 500));

        // REFUND_PENDING is the state OrderRefundService actually leaves an intent in: it sets
        // REFUND_FAILED only when the outbox enqueue itself throws, and enqueuing does not throw. So a
        // refund that is enqueued but never completed downstream stays REFUND_PENDING forever, and a
        // sweep that only looks at REFUND_FAILED is watching a status the failure mode never produces.
        //
        // Swept on updatedAt, not createdAt: an intent is stuck if it has not changed for a while,
        // whereas createdAt would make every refund of an older order look stale and retry a healthy
        // in-flight one. PaymentEventConsumer moves a completed refund to REFUNDED or
        // PARTIALLY_REFUNDED, which takes it out of this query.
        java.time.LocalDateTime stuckBefore = now.minusMinutes(STUCK_REFUND_PENDING_MINUTES);
        List<PaymentIntent> stuckPending = paymentIntentRepository
                .findByStatusAndUpdatedAtBefore(PaymentIntentStatus.REFUND_PENDING, stuckBefore,
                        org.springframework.data.domain.PageRequest.of(0, 500))
                .getContent();

        List<PaymentIntent> failedIntents = new java.util.ArrayList<>(failedIntentsPage.getContent());
        if (!stuckPending.isEmpty()) {
            log.warn("Found {} intents stuck in REFUND_PENDING for over {} minutes. Retrying...", stuckPending.size(), STUCK_REFUND_PENDING_MINUTES);
            io.micrometer.core.instrument.Metrics.counter("refund.retry.sweeper.stuck_pending").increment(stuckPending.size());
            failedIntents.addAll(stuckPending);
        }

        if (!failedIntents.isEmpty()) {
            log.info("Retrying {} refund intent(s) ({} failed, {} stuck pending).", failedIntents.size(), failedIntentsPage.getNumberOfElements(), stuckPending.size());
            for (PaymentIntent intent : failedIntents) {
                try {
                    if (intent.getRetryCount() >= 5) {
                        log.error("PaymentIntent {} has reached max retries for refund. Manual intervention required.", intent.getId());
                        io.micrometer.core.instrument.Metrics.counter("refund.retry.sweeper.max_limit_reached").increment();
                        
                        java.util.Map<String, Object> dlqPayload = new java.util.HashMap<>();
                        dlqPayload.put("intentId", intent.getId().toString());
                        dlqPayload.put("internalOrderId", intent.getInternalOrderId().toString());
                        dlqPayload.put("reason", "Refund retry limit exceeded");
                        
                        com.fooddelivery.common.outbox.entity.OutboxEventEntity dlqEvent = com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder()
                            .id(java.util.UUID.randomUUID())
                            .aggregateType(com.fooddelivery.common.constants.AggregateType.PAYMENT)
                            .aggregateId(intent.getId().toString())
                            .eventType(com.fooddelivery.common.constants.EventType.MANUAL_INTERVENTION_REQUIRED)
                            .payload(objectMapper.writeValueAsString(dlqPayload))
                            .createdAt(java.time.LocalDateTime.now())
                            .build();
                        outboxEventRepository.save(dlqEvent);
                        
                        // Set to 99 to avoid picking it up again and keep it permanently marked as escalated
                        intent.setRetryCount(99);
                        paymentIntentRepository.save(intent);
                        continue;
                    }

                    io.micrometer.core.instrument.Metrics.counter("refund.retry.sweeper.attempted").increment();
                    intent.setRetryCount(intent.getRetryCount() + 1);
                    paymentIntentRepository.save(intent);

                    Order order = orderRepository.findById(intent.getInternalOrderId()).orElse(null);
                    if (order != null) {
                        if (order.getStatus() == com.fooddelivery.common.enums.OrderStatus.CANCELLED || 
                            order.getStatus() == com.fooddelivery.common.enums.OrderStatus.CANCELLED_BY_RESTAURANT) {
                            log.info("Retrying refund for Order {}", order.getId());
                            orderRefundService.processRefund(order);
                        } else if (order.getStatus() == com.fooddelivery.common.enums.OrderStatus.HANDED_OVER) {
                            log.info("Checking for partial refund to retry for HANDED_OVER Order {}", order.getId());
                            java.util.List<com.fooddelivery.order.entity.SupportTicket> tickets = supportTicketRepository.findByOrderId(order.getId());
                            com.fooddelivery.order.entity.SupportTicket resolvedTicket = tickets.stream()
                                    .filter(t -> t.getStatus() == com.fooddelivery.order.entity.SupportTicket.TicketStatus.RESOLVED && t.getRefundAmount() != null)
                                    .findFirst()
                                    .orElse(null);
                            
                            if (resolvedTicket != null) {
                                log.info("Retrying partial refund of {} for Order {}", resolvedTicket.getRefundAmount(), order.getId());
                                orderRefundService.processPartialRefund(order, resolvedTicket.getRefundAmount());
                            } else {
                                log.warn("Skipping auto-retry for Order {}. Status is HANDED_OVER but no resolved SupportTicket found.", order.getId());
                            }
                        } else {
                            log.warn("Skipping auto-retry for Order {}. Status is {}.", order.getId(), order.getStatus());
                        }
                    } else {
                        log.warn("Order {} not found for PaymentIntent {}. Skipping.", intent.getInternalOrderId(), intent.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to retry refund for PaymentIntent {}", intent.getId(), e);
                }
            }
        }
    }

    
}
