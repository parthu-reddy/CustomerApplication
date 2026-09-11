package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.order.service.state.OrderActionService;
import java.util.UUID;
@lombok.extern.slf4j.Slf4j

public class ReadyForPickupState implements OrderState {
    

    @Override
    public void handleDriverAssigned(OrderContext ctx) {
        Order order = ctx.getOrder();
        String driverIdStr = ctx.getEventPayload().path("driverId").asText(null);
        try {
            UUID driverUUID = UUID.fromString(driverIdStr);
            order.setDeliveryExecutiveId(driverUUID);
            order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.ASSIGNED);
            // Notice: We do NOT transition back to DISPATCHED if already READY_FOR_PICKUP
            ctx.getActionService().saveOrder(order);
            ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.DRIVER_ON_THE_WAY);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid UUID format for driverId: " + driverIdStr, e);
        }
    }

    @Override
    public void handleDriverAtRestaurant(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.AT_RESTAURANT);
        ctx.getActionService().saveOrder(order);
    }

    @Override
    public void handleOrderHandedOver(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.HANDED_OVER);
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.OUT_FOR_DELIVERY);
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.DRIVER_ON_THE_WAY);
    }

    /** The one status update that is a real transition: the rider has picked the order up. */
    @Override
    public void handleStatusUpdate(OrderContext ctx) {
        String updateStatus = ctx.getEventPayload().path("status").asText(null);
        if (com.fooddelivery.common.enums.DeliveryStatus.OUT_FOR_DELIVERY.name().equals(updateStatus)) {
            handleOrderHandedOver(ctx);
            return;
        }
        OrderState.super.handleStatusUpdate(ctx);
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

    /**
     * The rider had the food and it did not arrive. Terminal, so the stuck-order sweeper does not
     * later cancel the same order a second time and attempt a second refund.
     */
    @Override
    public void handleDeliveryFailed(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.DELIVERY_FAILED);
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.FAILED);
        order.setCancellationReason(ctx.getEventPayload().path("reason").asText("Delivery failed"));
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.DELIVERY_FAILED);
        ctx.setRequiresRefund(true);
    }

    /**
     * The order was delivered without the handover ever being recorded.
     *
     * <p>The handover event was lost, retried into the DLT, or arrived after this one. Promote the
     * status first so the order does not end up READY_FOR_PICKUP with a delivery that says
     * DELIVERED, then complete it exactly as HandedOverState does -- this used to be a second,
     * quietly different implementation that skipped the COD cash entirely.
     */
    @Override
    public void handleOrderDelivered(OrderContext ctx) {
        Order order = ctx.getOrder();
        log.warn("Order {} was delivered without a recorded handover; promoting it to HANDED_OVER "
                + "before completing. The OUT_FOR_DELIVERY update was lost.", order.getId());
        order.setStatus(OrderStatus.HANDED_OVER);
        ctx.getActionService().completeDelivery(ctx);
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
}
