package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequiresManualInterventionState implements OrderState {

    @Override
    public void handleDriverAssigned(OrderContext ctx) {
        Order order = ctx.getOrder();
        String driverIdStr = ctx.getEventPayload().path("driverId").asText(null);
        try {
            UUID driverUUID = UUID.fromString(driverIdStr);
            order.setDeliveryExecutiveId(driverUUID);
            order.setStatus(OrderStatus.ACCEPTED);
            ctx.getActionService().saveOrder(order);
            ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.DRIVER_ON_THE_WAY.name());
            log.info("Driver {} assigned to order {} during manual intervention, status changed to ACCEPTED", driverUUID, order.getId());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid UUID format for driverId: " + driverIdStr, e);
        }
    }

    @Override
    public void handleOrderReady(OrderContext ctx) {
        Order order = ctx.getOrder();
        // Since it's in manual intervention, we might not want to change the status,
        // but we should record that it's ready. Changing status to READY_FOR_PICKUP
        // will remove it from the manual intervention queue, which is bad if no driver is assigned!
        // We'll just ignore it or log it, the admin will handle it.
        log.info("Restaurant marked order {} as ready while in REQUIRES_MANUAL_INTERVENTION. Ignoring status change until driver is assigned.", order.getId());
    }

    @Override
    public void handleOrderPreparing(OrderContext ctx) {
        Order order = ctx.getOrder();
        log.info("Restaurant marked order {} as preparing while in REQUIRES_MANUAL_INTERVENTION. Ignoring status change until driver is assigned.", order.getId());
    }

    @Override
    public void handleOrderCancelledByRestaurant(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
        order.setCancellationReason(ctx.getEventPayload().path("reason").asText("Restaurant could not fulfill the order"));
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_CANCELLED_BY_RESTAURANT.name());
        ctx.setRequiresRefund(true);
    }
    
    @Override
    public void cancelByCustomer(OrderContext ctx, String reason) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason != null ? reason : "Cancelled by customer");
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_CANCELLED_BY_CUSTOMER.name());
        ctx.setRequiresRefund(true);
    }
    
    // Custom method to handle admin cancellation, invoked via orchestrator or custom mapping
    public void handleOrderCancelledByAdmin(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(ctx.getEventPayload().path("reason").asText("Cancelled by Admin"));
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_CANCELLED_BY_ADMIN.name());
        ctx.setRequiresRefund(true);
    }
    
    @Override
    public void handleStatusUpdate(OrderContext ctx) {
        Order order = ctx.getOrder();
        String updateStatus = ctx.getEventPayload().path("status").asText(null);
        if (updateStatus != null) {
            try {
                OrderStatus newStatus = OrderStatus.valueOf(updateStatus);
                order.setStatus(newStatus);
                ctx.getActionService().saveOrder(order);
                log.info("Order {} status updated to {} via status update from REQUIRES_MANUAL_INTERVENTION", order.getId(), newStatus);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status {} in status update for Order {}", updateStatus, order.getId());
            }
        }
    }
}
