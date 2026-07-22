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
public class AbandonedDeliverySweeper {

    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 300000)
    public void sweepAbandonedDeliveries() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(2);
        
        sweepByStatus(OrderStatus.PICKED_UP, threshold);
    }
    
    private void sweepByStatus(OrderStatus status, LocalDateTime threshold) {
        List<Order> abandonedOrders = orderRepository.findByStatusAndUpdatedAtBefore(status, threshold);
        
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
                if (currentOrder != null && currentOrder.getStatus() == OrderStatus.PICKED_UP) {
                    currentOrder.setStatus(OrderStatus.DELIVERY_FAILED);
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
}
