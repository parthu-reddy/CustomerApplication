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
public class RefundRetrySweeper {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RefundRetrySweeper.class);
    private final IPaymentIntentRepository paymentIntentRepository;
    private final IOrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final StringRedisTemplate redisTemplate;
    private final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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

                    intent.setRetryCount(intent.getRetryCount() + 1);
                    paymentIntentRepository.save(intent);

                    Order order = orderRepository.findById(intent.getInternalOrderId()).orElse(null);
                    if (order != null) {
                        if (order.getStatus() == com.fooddelivery.common.enums.OrderStatus.CANCELLED || 
                            order.getStatus() == com.fooddelivery.common.enums.OrderStatus.CANCELLED_BY_RESTAURANT) {
                            log.info("Retrying refund for Order {}", order.getId());
                            orderSagaOrchestrator.processRefund(order);
                        } else {
                            log.warn("Skipping auto-retry for Order {}. Status is {} (likely a manual partial refund failure).", order.getId(), order.getStatus());
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
    public RefundRetrySweeper(final IPaymentIntentRepository paymentIntentRepository, final IOrderRepository orderRepository, final OrderSagaOrchestrator orderSagaOrchestrator, final StringRedisTemplate redisTemplate, final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository, final com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.orderRepository = orderRepository;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.redisTemplate = redisTemplate;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }
}
