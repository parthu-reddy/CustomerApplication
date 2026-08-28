package com.fooddelivery.customer.service;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
/**
 * <strong>@replication-safe: optimistic-lock</strong> -- re-reads each order inside the transaction and Order carries @Version, so a concurrent duplicate cancel fails the version check and rolls back with its outbox row.
 *
 * <p>Classification recorded 2026-08-27 (Phase 7). Every @Scheduled class in this workspace
 * carries one of these markers; the BOOT-SCHEDULE-CLASSIFIED check fails on a new one that
 * does not. Change the marker only after re-reading what the job actually does.
 */
public class OrderReconciliationService {
    

    private final IOrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final TransactionTemplate transactionTemplate;
    private final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository;

    /**
     * Sweeps for orders that have been stuck in CREATED (INITIATED) state for more than 15 minutes.
     * This happens if payment intent generation completely failed and the initial compensation also failed,
     * or if the customer abandoned the payment intent generation screen before redirect.
     * Runs every 5 minutes.
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void cancelDanglingInitiatedOrders() {
        log.info("RECONCILIATION: Starting sweep for dangling CREATED orders...");
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        List<Order> danglingOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.CREATED, threshold);
        if (danglingOrders.isEmpty()) {
            log.info("RECONCILIATION: No dangling CREATED orders found.");
            return;
        }
        log.warn("RECONCILIATION: Found {} dangling CREATED orders. Cancelling them...", danglingOrders.size());
        for (Order order : danglingOrders) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    // Refetch with lock if necessary, but optimistic locking works here
                    Order freshOrder = orderRepository.findById(order.getId()).orElse(null);
                    if (freshOrder != null && freshOrder.getStatus() == OrderStatus.CREATED) {
                        freshOrder.setStatus(OrderStatus.CANCELLED);
                        freshOrder.setCancellationReason("System reconciliation: Payment intent timed out or abandoned");
                        orderRepository.save(freshOrder);
                        // Trigger compensation event
                        com.fooddelivery.common.outbox.entity.OutboxEventEntity cancelEvent = com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(freshOrder.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.ORDER_CANCELLED).payload(String.format("{\"eventType\":\"ORDER_CANCELLED\", \"orderId\":\"%s\", \"reason\":\"System reconciliation: Payment intent timed out\"}", freshOrder.getId())).createdAt(java.time.LocalDateTime.now()).build();
                        outboxEventRepository.save(cancelEvent);
                        log.info("RECONCILIATION: Cancelled order {} successfully.", freshOrder.getId());
                    }
                });
            } catch (Exception e) {
                log.error("RECONCILIATION: Failed to cancel dangling order {}", order.getId(), e);
            }
        }
        log.info("RECONCILIATION: Sweep completed.");
    }

    
}
