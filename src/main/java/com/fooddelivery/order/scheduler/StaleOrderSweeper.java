package com.fooddelivery.order.scheduler;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
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
public class StaleOrderSweeper {

    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 60000)
    public void sweepStaleCreatedOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        List<Order> staleOrders = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.CREATED, threshold);
        
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
}
