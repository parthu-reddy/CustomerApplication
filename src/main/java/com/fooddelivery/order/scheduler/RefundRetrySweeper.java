package com.fooddelivery.order.scheduler;

import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class RefundRetrySweeper {

    private final IPaymentIntentRepository paymentIntentRepository;
    private final IOrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 300000) // Runs every 5 minutes
    public void retryFailedRefunds() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent("lock:sweepRefundRetries", "1", java.time.Duration.ofSeconds(200));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        List<PaymentIntent> failedIntents = paymentIntentRepository.findByStatusAndCreatedAtBefore(
            PaymentIntentStatus.REFUND_FAILED, 
            java.time.LocalDateTime.now()
        );
        
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
}
