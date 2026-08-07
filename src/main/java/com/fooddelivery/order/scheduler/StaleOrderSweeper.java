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
public class StaleOrderSweeper {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StaleOrderSweeper.class);
    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final TransactionTemplate transactionTemplate;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 60000)
    public void sweepStaleCreatedOrders() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(com.fooddelivery.common.constants.RedisKeyConstants.LOCK_SWEEP_STALE_CREATED_ORDERS, "1", java.time.Duration.ofSeconds(50));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        org.springframework.data.domain.Page<Order> staleOrdersPage = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.CREATED, threshold, org.springframework.data.domain.PageRequest.of(0, 500));
        List<Order> staleOrders = staleOrdersPage.getContent();
        if (!staleOrders.isEmpty()) {
            log.info("Found {} stale CREATED orders. Cancelling them...", staleOrders.size());
            for (Order order : staleOrders) {
                cancelStaleOrder(order);
            }
        }
    }

    private void cancelStaleOrder(Order order) {
        try {
            transactionTemplate.execute(status -> {
                // Re-fetch with lock if needed, but since it's CREATED and old, a simple save is usually fine
                Order currentOrder = orderRepository.findById(order.getId()).orElse(null);
                if (currentOrder != null && currentOrder.getStatus() == OrderStatus.CREATED) {
                    currentOrder.setStatus(OrderStatus.CANCELLED);
                    currentOrder.setCancellationReason("Payment timeout after 15 minutes");
                    orderRepository.save(currentOrder);
                    orderActionService.emitOrderCancelledEvent(currentOrder.getId(), "Customer failed to complete payment within 15 minutes");
                    log.info("Auto-cancelled stale order {}", currentOrder.getId());
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to cancel stale order {}", order.getId(), e);
        }
    }

    @java.lang.SuppressWarnings("all")
    public StaleOrderSweeper(final IOrderRepository orderRepository, final OrderActionService orderActionService, final TransactionTemplate transactionTemplate, final org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.orderRepository = orderRepository;
        this.orderActionService = orderActionService;
        this.transactionTemplate = transactionTemplate;
        this.redisTemplate = redisTemplate;
    }
}
