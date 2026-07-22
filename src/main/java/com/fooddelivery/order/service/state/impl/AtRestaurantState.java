package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderContext;

public class AtRestaurantState extends DispatchedState {

    @Override
    public void handleStatusUpdate(OrderContext ctx) {
        String updateStatus = ctx.getEventPayload().path("status").asText(null);
        if (OrderStatus.OUT_FOR_DELIVERY.name().equals(updateStatus)) {
            Order order = ctx.getOrder();
            
            String providedOtp = ctx.getEventPayload().path("pickupOtp").asText(null);
            if (providedOtp == null || !providedOtp.equals(order.getPickupOtp())) {
                throw new com.fooddelivery.order.service.state.IllegalStateTransitionException("Invalid or missing pickup OTP. Cannot transition to OUT_FOR_DELIVERY.");
            }
            
            order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
            ctx.getActionService().saveOrder(order);
            ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.DRIVER_ON_THE_WAY.name());
        } else {
            super.handleStatusUpdate(ctx);
        }
    }
}
