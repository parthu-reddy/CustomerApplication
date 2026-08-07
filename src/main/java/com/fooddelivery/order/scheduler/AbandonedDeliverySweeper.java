package com.fooddelivery.order.scheduler;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class AbandonedDeliverySweeper {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AbandonedDeliverySweeper.class);
    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final TransactionTemplate transactionTemplate;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 300000)
    public void sweepAbandonedDeliveries() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(com.fooddelivery.common.constants.RedisKeyConstants.LOCK_SWEEP_ABANDONED_DELIVERIES, "1", java.time.Duration.ofSeconds(200));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusHours(2);
        sweepByStatus(OrderStatus.HANDED_OVER, threshold);
    }

    private void sweepByStatus(OrderStatus status, LocalDateTime threshold) {
        org.springframework.data.domain.Page<Order> page = orderRepository.findByStatusAndUpdatedAtBefore(status, threshold, org.springframework.data.domain.PageRequest.of(0, 500));
        List<Order> abandonedOrders = page.getContent();
        if (!abandonedOrders.isEmpty()) {
            log.info("Found {} abandoned {} orders. Marking them as DELIVERY_FAILED...", abandonedOrders.size(), status);
            for (Order order : abandonedOrders) {
                failAbandonedOrder(order);
            }
        }
    }

    private void failAbandonedOrder(Order order) {
        try {
            transactionTemplate.execute(status -> {
                Order currentOrder = orderRepository.findById(order.getId()).orElse(null);
                if (currentOrder != null && currentOrder.getStatus() == OrderStatus.HANDED_OVER) {
                    currentOrder.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.FAILED);
                    orderRepository.save(currentOrder);
                    orderActionService.emitOrderDeliveryFailedEvent(currentOrder.getId(), "Driver abandoned the delivery (no updates for 2 hours)");
                    log.info("Marked abandoned order {} as DELIVERY_FAILED", currentOrder.getId());
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to mark abandoned order {} as DELIVERY_FAILED", order.getId(), e);
        }
    }

    @java.lang.SuppressWarnings("all")
    public AbandonedDeliverySweeper(final IOrderRepository orderRepository, final OrderActionService orderActionService, final TransactionTemplate transactionTemplate, final org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.orderRepository = orderRepository;
        this.orderActionService = orderActionService;
        this.transactionTemplate = transactionTemplate;
        this.redisTemplate = redisTemplate;
    }
}
