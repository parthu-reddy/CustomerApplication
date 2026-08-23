package com.fooddelivery.order.service.state.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
@lombok.extern.slf4j.Slf4j

public class PendingAcceptanceState implements OrderState {
    

    @Override
    public void handleOrderAccepted(OrderContext ctx) {
        log.info("Order {} accepted by restaurant", ctx.getOrder().getId());
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.ACCEPTED);
        JsonNode prepNode = ctx.getEventPayload().path("estimatedPrepTimeMinutes");
        if (!prepNode.isMissingNode() && !prepNode.isNull()) {
            order.setEstimatedPrepTimeMinutes(prepNode.asInt());
        }
        JsonNode completionNode = ctx.getEventPayload().path("estimatedCompletionTime");
        if (!completionNode.isMissingNode() && !completionNode.isNull()) {
            order.setEstimatedCompletionTime(completionNode.asLong());
        }
        ctx.getActionService().saveOrder(order);
    }

    @Override
    public void handleDelayApprovalRequested(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.AWAITING_DELAY_APPROVAL);
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.DELAY_APPROVAL_REQUESTED.name());
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
        // Notify restaurant to cancel (since it was paid)
        ctx.getActionService().emitOrderCancelledByCustomerEvent(order.getId());
        ctx.setRequiresRefund(true); // Full refund when customer cancels before restaurant accepts
    }

    @Override
    public void handleOrderCancelledByAdmin(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(ctx.getEventPayload().path("reason").asText("Cancelled by Admin"));
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_CANCELLED_BY_ADMIN.name());
        ctx.setRequiresRefund(true);
    }
}
