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
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
/**
 * <strong>@replication-safe: distributed-lock</strong> -- guarded by a Redis lock before any timeout is actioned.
 *
 * <p>Classification recorded 2026-08-27 (Phase 7). Every @Scheduled class in this workspace
 * carries one of these markers; the BOOT-SCHEDULE-CLASSIFIED check fails on a new one that
 * does not. Change the marker only after re-reading what the job actually does.
 */
public class RestaurantTimeoutSweeper {
    

    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final OrderSagaOrchestrator orderSagaOrchestrator;

    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate redisTemplate;
    private final com.fooddelivery.order.refund.RefundService refundService;

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
    public void sweepStuckRestaurantOrders() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(com.fooddelivery.common.constants.RedisKeyConstants.LOCK_SWEEP_RESTAURANT_TIMEOUTS + "_STUCK", "1", Duration.ofSeconds(50));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(60);
        List<OrderStatus> stuckStatuses = List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP);
        org.springframework.data.domain.Page<Order> stuckOrdersPage = orderRepository.findByStatusInAndUpdatedAtBefore(stuckStatuses, threshold, org.springframework.data.domain.PageRequest.of(0, 500));
        List<Order> stuckOrders = stuckOrdersPage.getContent();
        if (!stuckOrders.isEmpty()) {
            log.info("Found {} orders stuck in restaurant states (ACCEPTED/PREPARING/READY) for > 60 mins. Cancelling them...", stuckOrders.size());
            for (Order order : stuckOrders) {
                cancelStuckOrder(order, "Auto-cancelled: Restaurant failed to process or handover the order in time (60 mins)");
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
                com.fooddelivery.order.refund.RefundCommand cmd = com.fooddelivery.order.refund.RefundCommand.builder()
                        .orderId(order.getId())
                        .amount(order.getTotalAmount())
                        .faultType(com.fooddelivery.order.enums.FaultType.RESTAURANT_FAULT)
                        .destination(com.fooddelivery.common.enums.RefundDestination.ORIGINAL_METHOD)
                        .initiatorType(com.fooddelivery.order.enums.InitiatorType.SYSTEM)
                        .reasonCode("AUTO_CANCEL_STALE")
                        .idempotencyKey("sweep_stale_" + order.getId())
                        .build();
                refundService.request(cmd);
            }
        } catch (Exception e) {
            log.error("Failed to auto-cancel timeout order {}", order.getId(), e);
        }
    }

    private void cancelStuckOrder(Order order, String reason) {
        try {
            boolean refundNeeded = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                Order currentOrder = orderRepository.findById(order.getId()).orElse(null);
                if (currentOrder != null && (currentOrder.getStatus() == OrderStatus.ACCEPTED || 
                                             currentOrder.getStatus() == OrderStatus.PREPARING || 
                                             currentOrder.getStatus() == OrderStatus.READY_FOR_PICKUP)) {
                    currentOrder.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT); 
                    currentOrder.setCancellationReason(reason);
                    orderRepository.save(currentOrder);
                    orderActionService.emitOrderCancelledByRestaurantEvent(currentOrder.getId(), currentOrder.getCancellationReason());
                    log.info("Auto-cancelled stuck order {}", currentOrder.getId());
                    return true;
                }
                return false;
            }));
            if (refundNeeded) {
                com.fooddelivery.order.refund.RefundCommand cmd = com.fooddelivery.order.refund.RefundCommand.builder()
                        .orderId(order.getId())
                        .amount(order.getTotalAmount())
                        .faultType(com.fooddelivery.order.enums.FaultType.RESTAURANT_FAULT)
                        .destination(com.fooddelivery.common.enums.RefundDestination.ORIGINAL_METHOD)
                        .initiatorType(com.fooddelivery.order.enums.InitiatorType.SYSTEM)
                        .reasonCode("AUTO_CANCEL_STUCK")
                        .idempotencyKey("sweep_stuck_" + order.getId())
                        .build();
                refundService.request(cmd);
            }
        } catch (Exception e) {
            log.error("Failed to auto-cancel stuck order {}", order.getId(), e);
        }
    }

    
}
