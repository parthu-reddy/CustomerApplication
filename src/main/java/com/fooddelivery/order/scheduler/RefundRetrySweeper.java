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

    // Runs every 5 minutes
    @Scheduled(fixedDelay = 300000)
    public void retryFailedRefunds() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(com.fooddelivery.common.constants.RedisKeyConstants.LOCK_SWEEP_REFUND_RETRIES, "1", java.time.Duration.ofSeconds(200));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        org.springframework.data.domain.Page<PaymentIntent> failedIntentsPage = paymentIntentRepository.findByStatusAndCreatedAtBefore(PaymentIntentStatus.REFUND_FAILED, java.time.LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, 500));
        List<PaymentIntent> failedIntents = failedIntentsPage.getContent();
        if (!failedIntents.isEmpty()) {
            log.info("Found {} intents with REFUND_FAILED status. Retrying...", failedIntents.size());
            for (PaymentIntent intent : failedIntents) {
                try {
                    Order order = orderRepository.findById(intent.getInternalOrderId()).orElse(null);
                    if (order != null) {
                        log.info("Retrying refund for Order {}", order.getId());
                        orderSagaOrchestrator.processRefund(order);
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
    public RefundRetrySweeper(final IPaymentIntentRepository paymentIntentRepository, final IOrderRepository orderRepository, final OrderSagaOrchestrator orderSagaOrchestrator, final StringRedisTemplate redisTemplate) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.orderRepository = orderRepository;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.redisTemplate = redisTemplate;
    }
}
