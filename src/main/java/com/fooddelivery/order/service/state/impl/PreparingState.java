package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
import java.util.UUID;
@lombok.extern.slf4j.Slf4j

public class PreparingState implements OrderState {
    

    @Override
    public void handleOrderReady(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.ORDER_READY_FOR_PICKUP);
    }

    @Override
    public void handleDriverAtRestaurant(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.AT_RESTAURANT);
        ctx.getActionService().saveOrder(order);
    }

    @Override
    public void handleDriverAssigned(OrderContext ctx) {
        Order order = ctx.getOrder();
        String driverIdStr = ctx.getEventPayload().path("driverId").asText(null);
        try {
            UUID driverUUID = UUID.fromString(driverIdStr);
            order.setDeliveryExecutiveId(driverUUID);
            order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.ASSIGNED);
            ctx.getActionService().saveOrder(order);
            ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.DRIVER_ON_THE_WAY);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid UUID format for driverId: " + driverIdStr, e);
        }
    }

    @Override
    public void handleOrderCancelledByRestaurant(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
        order.setCancellationReason(ctx.getEventPayload().path("reason").asText("Restaurant could not fulfill the order"));
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.ORDER_CANCELLED_BY_RESTAURANT);
        ctx.setRequiresRefund(true);
    }

    /**
     * No driver could be assigned. The order ends here, and the platform pays for it.
     *
     * <p>This used to set the delivery status and stop: no terminal status, no refund. The order sat
     * paid, with no driver, in ACCEPTED/PREPARING/READY_FOR_PICKUP until the 60-minute stuck-order
     * sweeper cancelled it as CANCELLED_BY_RESTAURANT with RESTAURANT_FAULT -- billing the
     * restaurant, an hour late, for the platform's failure to find a rider.
     */
    @Override
    public void handleDispatchFailed(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED_BY_PLATFORM);
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.FAILED);
        order.setCancellationReason(ctx.getEventPayload().path("reason")
                .asText("No delivery partner could be assigned"));
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.DISPATCH_FAILED);
        ctx.setRequiresRefund(true);
    }

    @Override
    public void handleOrderCancelledByAdmin(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(ctx.getEventPayload().path("reason").asText("Cancelled by Admin"));
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.ORDER_CANCELLED_BY_ADMIN);
        ctx.setRequiresRefund(true);
    }

    @Override
    public void handleManualInterventionRequired(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.MANUAL_INTERVENTION_REQUIRED);
        ctx.getActionService().saveOrder(order);
        log.info("Manual intervention required for order {}. Delivery status set to MANUAL_INTERVENTION_REQUIRED.", order.getId());
    }
}
