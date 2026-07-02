package com.fooddelivery.order.service.state.impl;

import com.fasterxml.jackson.databind.JsonNode;
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

        JsonNode prepNode = ctx.getEventPayload().path("estimatedPrepTimeMinutes");
        if (!prepNode.isMissingNode() && !prepNode.isNull()) {
            order.setEstimatedPrepTimeMinutes(prepNode.asInt());
        }
        
        ctx.getActionService().saveOrder(order);
    }

    @Override
    public void handleDelayRejected(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().emitOrderCancelledEvent(order.getId(), "Customer rejected delay");
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_DELAY_REJECTED);
        ctx.setRequiresRefund(true);
    }

    @Override
    public void handleOrderCancelledByRestaurant(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
        order.setCancellationReason(ctx.getEventPayload().path("reason").asText("Restaurant could not fulfill the order"));
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_CANCELLED_BY_RESTAURANT);
        ctx.setRequiresRefund(true);
    }
}
