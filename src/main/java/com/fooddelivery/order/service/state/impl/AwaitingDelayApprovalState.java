package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;

public class AwaitingDelayApprovalState implements OrderState {

    @Override
    public void handleOrderAccepted(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.ACCEPTED);

        Integer prepNode = ((com.fooddelivery.common.event.OrderAcceptedEvent) ctx.getEventPayload()).getEstimatedPrepTimeMinutes();
        Long completionNode = ((com.fooddelivery.common.event.OrderAcceptedEvent) ctx.getEventPayload()).getEstimatedCompletionTime();
        if (prepNode != null) {
            order.setEstimatedPrepTimeMinutes(prepNode);
        }
        if (completionNode != null) {
            order.setEstimatedCompletionTime(completionNode);
        }
        
        ctx.getActionService().saveOrder(order);
    }

    @Override
    public void handleDelayRejected(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
        order.setCancellationReason("Customer rejected delay");
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.ORDER_DELAY_REJECTED);
        ctx.setRequiresRefund(true);
    }
    
    @Override
    public void handleOrderCancelledByRestaurant(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
        String reason = null; if (ctx.getEventPayload() instanceof com.fooddelivery.common.event.OrderCancelledByRestaurantEvent) { reason = ((com.fooddelivery.common.event.OrderCancelledByRestaurantEvent) ctx.getEventPayload()).getReason(); } else if (ctx.getEventPayload() instanceof com.fooddelivery.common.event.OrderRejectedEvent) { reason = ((com.fooddelivery.common.event.OrderRejectedEvent) ctx.getEventPayload()).getReason(); } order.setCancellationReason(reason != null ? reason : "Restaurant could not fulfill the order");
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.ORDER_CANCELLED_BY_RESTAURANT);
        ctx.setRequiresRefund(true);
    }

    @Override
    public void cancelByCustomer(OrderContext ctx, String reason) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason != null ? reason : "Cancelled by customer");
        ctx.getActionService().saveOrder(order);

        // Notify restaurant to cancel (since it was paid)
        ctx.getActionService().emitOrderCancelledByCustomerEvent(order.getId());
        ctx.setRequiresRefund(true);
    }

    @Override
    public void handleOrderCancelledByAdmin(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        String reason = ((com.fooddelivery.common.event.OrderCancelledByAdminEvent) ctx.getEventPayload()).getReason(); order.setCancellationReason(reason != null ? reason : "Cancelled by Admin");
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.ORDER_CANCELLED_BY_ADMIN);
        ctx.setRequiresRefund(true);
    }
}
