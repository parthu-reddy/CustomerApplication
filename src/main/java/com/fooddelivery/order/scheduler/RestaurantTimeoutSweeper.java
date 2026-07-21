package com.fooddelivery.order.scheduler;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import com.fooddelivery.order.service.state.OrderActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class RestaurantTimeoutSweeper {

    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 60000)
    public void sweepStalePaidOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<Order> staleOrders = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PAID, threshold);
        
        if (!staleOrders.isEmpty()) {
            log.info("Found {} stale PAID orders (Restaurant Timeout). Cancelling them...", staleOrders.size());
            for (Order order : staleOrders) {
                cancelStaleOrder(order, "Auto-cancelled: Restaurant did not respond within 10 minutes");
            }
        }
        
        List<Order> staleDelayApprovals = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.AWAITING_DELAY_APPROVAL, threshold);
        if (!staleDelayApprovals.isEmpty()) {
            log.info("Found {} stale AWAITING_DELAY_APPROVAL orders. Cancelling them...", staleDelayApprovals.size());
            for (Order order : staleDelayApprovals) {
                cancelStaleOrder(order, "Auto-cancelled: Customer did not respond to delay request within 10 minutes");
            }
        }
    }
    
    private void cancelStaleOrder(Order order, String reason) {
        try {
            boolean refundNeeded = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                // Re-fetch with lock
                Order currentOrder = orderRepository.findById(order.getId()).orElse(null);
                if (currentOrder != null && (currentOrder.getStatus() == OrderStatus.PAID || currentOrder.getStatus() == OrderStatus.AWAITING_DELAY_APPROVAL)) {
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
}
