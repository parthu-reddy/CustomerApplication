package com.fooddelivery.order.service.state;

import com.fooddelivery.order.entity.Order;

public interface OrderState {

    default void handlePaymentSuccess(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process payment success. Current status: " + ctx.getOrder().getStatus());
    }

    default void handlePaymentFailure(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process payment failure. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleOrderAccepted(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process ORDER_ACCEPTED. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleOrderReady(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process ORDER_READY. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleDriverAssigned(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process DRIVER_ASSIGNED. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleOrderDelivered(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process ORDER_DELIVERED. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleDelayApprovalRequested(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process ORDER_DELAY_APPROVAL_REQUESTED. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleDelayRejected(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process ORDER_DELAY_REJECTED. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleOrderCancelledByRestaurant(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process ORDER_CANCELLED_BY_RESTAURANT. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleDispatchFailed(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process DISPATCH_FAILED. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleDeliveryFailed(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process DELIVERY_FAILED. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleStatusUpdate(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process arbitrary status update. Current status: " + ctx.getOrder().getStatus());
    }
}
