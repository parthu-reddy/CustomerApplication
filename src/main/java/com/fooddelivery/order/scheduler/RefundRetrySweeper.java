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
public class RefundRetrySweeper {
    @java.lang.SuppressWarnings("all")

    private final IPaymentIntentRepository paymentIntentRepository;
    private final IOrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final com.fooddelivery.order.service.OrderRefundService orderRefundService;
    private final StringRedisTemplate redisTemplate;
    private final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.fooddelivery.order.repository.SupportTicketRepository supportTicketRepository;

    // Runs every 5 minutes
    @Scheduled(fixedDelay = 300000)
    public void retryFailedRefunds() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(com.fooddelivery.common.constants.RedisKeyConstants.LOCK_SWEEP_REFUND_RETRIES, "1", java.time.Duration.ofSeconds(200));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime minTime = now.minusHours(48);
        org.springframework.data.domain.Page<PaymentIntent> failedIntentsPage = paymentIntentRepository.findByStatusAndCreatedAtBetween(PaymentIntentStatus.REFUND_FAILED, minTime, now, org.springframework.data.domain.PageRequest.of(0, 500));
        List<PaymentIntent> failedIntents = failedIntentsPage.getContent();
        if (!failedIntents.isEmpty()) {
            log.info("Found {} intents with REFUND_FAILED status. Retrying...", failedIntents.size());
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
                                orderRefundService.processPartialRefund(order, java.math.BigDecimal.valueOf(resolvedTicket.getRefundAmount()));
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

    @java.lang.SuppressWarnings("all")
    public RefundRetrySweeper(final IPaymentIntentRepository paymentIntentRepository, final IOrderRepository orderRepository, final OrderSagaOrchestrator orderSagaOrchestrator, final com.fooddelivery.order.service.OrderRefundService orderRefundService, final StringRedisTemplate redisTemplate, final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository, final com.fasterxml.jackson.databind.ObjectMapper objectMapper, final com.fooddelivery.order.repository.SupportTicketRepository supportTicketRepository) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.orderRepository = orderRepository;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.orderRefundService = orderRefundService;
        this.redisTemplate = redisTemplate;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.supportTicketRepository = supportTicketRepository;
    }
}
