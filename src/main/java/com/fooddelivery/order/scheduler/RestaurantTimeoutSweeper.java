package com.fooddelivery.order.scheduler;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import com.fooddelivery.order.service.state.OrderActionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class RestaurantTimeoutSweeper {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RestaurantTimeoutSweeper.class);
    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 60000)
    public void sweepStalePaidOrders() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(com.fooddelivery.common.constants.RedisKeyConstants.LOCK_SWEEP_RESTAURANT_TIMEOUTS, "1", Duration.ofSeconds(50));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        org.springframework.data.domain.Page<Order> staleOrdersPage = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PENDING_ACCEPTANCE, threshold, org.springframework.data.domain.PageRequest.of(0, 500));
        List<Order> staleOrders = staleOrdersPage.getContent();
        if (!staleOrders.isEmpty()) {
            log.info("Found {} stale PAID orders (Restaurant Timeout). Cancelling them...", staleOrders.size());
            for (Order order : staleOrders) {
                cancelStaleOrder(order, "Auto-cancelled: Restaurant did not respond within 10 minutes");
            }
        }

    }

    @Scheduled(fixedDelay = 60000)
    public void sweepDelayApprovalTimeouts() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(com.fooddelivery.common.constants.RedisKeyConstants.LOCK_SWEEP_RESTAURANT_TIMEOUTS + "_DELAY", "1", Duration.ofSeconds(50));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(10);
        org.springframework.data.domain.Page<Order> staleOrdersPage = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.AWAITING_DELAY_APPROVAL, cutoffTime, org.springframework.data.domain.PageRequest.of(0, 500));
        List<Order> delayedOrders = staleOrdersPage.getContent();
        if (!delayedOrders.isEmpty()) {
            log.info("Found {} stale AWAITING_DELAY_APPROVAL orders. Cancelling them...", delayedOrders.size());
            for (Order order : delayedOrders) {
                log.info("Order {} exceeded 10-minute delay approval timeout. Cancelling order.", order.getId());
                boolean eventPublished = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                    Order dbOrder = orderRepository.findById(order.getId()).orElse(null);
                    if (dbOrder != null && dbOrder.getStatus() == OrderStatus.AWAITING_DELAY_APPROVAL) {
                        orderSagaOrchestrator.publishDelayApprovalEvent(dbOrder, false, "Auto-cancelled: Customer did not respond to delay approval in 10 minutes");
                        return true;
                    }
                    return false;
                }));
                if (eventPublished) {
                    log.info("Published ORDER_DELAY_REJECTED for order {} due to timeout.", order.getId());
                }
            }
        }
    }

    private void cancelStaleOrder(Order order, String reason) {
        try {
            boolean refundNeeded = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                // Re-fetch with lock
                Order currentOrder = orderRepository.findById(order.getId()).orElse(null);
                if (currentOrder != null && currentOrder.getStatus() == OrderStatus.PENDING_ACCEPTANCE) {
                    currentOrder.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT); // Use restaurant cancellation so refund triggers
                    currentOrder.setCancellationReason(reason);
                    orderRepository.save(currentOrder);
                    orderActionService.emitOrderCancelledByRestaurantEvent(currentOrder.getId(), currentOrder.getCancellationReason());
                    log.info("Auto-cancelled timeout order {}", currentOrder.getId());
                    return true;
                }
                return false;
            }));
            if (refundNeeded) {
                // Process refund outside the transaction lock to avoid hanging on HTTP calls
                orderSagaOrchestrator.processRefund(order);
            }
        } catch (Exception e) {
            log.error("Failed to auto-cancel timeout order {}", order.getId(), e);
        }
    }

    @java.lang.SuppressWarnings("all")
    public RestaurantTimeoutSweeper(final IOrderRepository orderRepository, final OrderActionService orderActionService, final OrderSagaOrchestrator orderSagaOrchestrator, final TransactionTemplate transactionTemplate, final StringRedisTemplate redisTemplate) {
        this.orderRepository = orderRepository;
        this.orderActionService = orderActionService;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.transactionTemplate = transactionTemplate;
        this.redisTemplate = redisTemplate;
    }
}
