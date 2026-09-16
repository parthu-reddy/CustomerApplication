package com.fooddelivery.order.service.state;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.exception.IllegalStateTransitionException;

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

    default void handleOrderPreparing(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process ORDER_PREPARING. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleOrderReady(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process ORDER_READY. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleDriverAssigned(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process DRIVER_ASSIGNED. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleDriverAtRestaurant(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process DRIVER_AT_RESTAURANT. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleOrderHandedOver(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process HANDED_OVER. Current status: " + ctx.getOrder().getStatus());
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

    default void handleOrderCancelledByAdmin(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process ORDER_CANCELLED_BY_ADMIN. Current status: " + ctx.getOrder().getStatus());
    }

    default void cancelByCustomer(OrderContext ctx, String reason) {
        throw new IllegalStateTransitionException("Cannot cancel order. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleDispatchFailed(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process DISPATCH_FAILED. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleManualInterventionRequired(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process MANUAL_INTERVENTION_REQUIRED in state: " + ctx.getOrder().getStatus());
    }

    default void handleDeliveryFailed(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process DELIVERY_FAILED. Current status: " + ctx.getOrder().getStatus());
    }

    /**
     * There is no generic "set the status to whatever the payload says".
     *
     * <p>This used to fast-forward to any status with a higher sequence, taken straight from an
     * {@code ORDER_STATUS_UPDATED} payload -- so an event could move an order to HANDED_OVER
     * without it ever having been accepted, prepared or made ready. Every legitimate transition has
     * a named handler; the one case this existed for is {@code ReadyForPickupState} promoting
     * OUT_FOR_DELIVERY to HANDED_OVER, which that class overrides.
     */
    default void handleStatusUpdate(OrderContext ctx) {
        throw new IllegalStateTransitionException(
                "Cannot process arbitrary status update. Current status: " + ctx.getOrder().getStatus());
    }
}
