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

    default void cancelByCustomer(OrderContext ctx, String reason) {
        throw new IllegalStateTransitionException("Cannot cancel order. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleDispatchFailed(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process DISPATCH_FAILED. Current status: " + ctx.getOrder().getStatus());
    }

    default void handleDeliveryFailed(OrderContext ctx) {
        throw new IllegalStateTransitionException("Cannot process DELIVERY_FAILED. C`urrent status: " + ctx.getOrder().getStatus());
    }

    default void handleStatusUpdate(OrderContext ctx) {
        String updateStatusStr = ctx.getEventPayload().path("status").asText(null);
        if (updateStatusStr != null) {
            try {
                OrderStatus newStatus = OrderStatus.valueOf(updateStatusStr);
                OrderStatus currentStatus = ctx.getOrder().getStatus();
                
                if (newStatus.getSequence() > currentStatus.getSequence()) {
                    ctx.getOrder().setStatus(newStatus);
                    if (newStatus == OrderStatus.PICKED_UP) {
                        ctx.getOrder().setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.OUT_FOR_DELIVERY);
                    } else if (newStatus == OrderStatus.DELIVERED) {
                        ctx.getOrder().setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED);
                    } else if (newStatus == OrderStatus.DELIVERY_FAILED) {
                        ctx.getOrder().setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.FAILED);
                    }
                    ctx.getActionService().saveOrder(ctx.getOrder());
                    return; // Successfully fast-forwarded
                }
            } catch (IllegalArgumentException e) {
                // Ignore invalid status mapping here
            }
        }
        throw new IllegalStateTransitionException("Cannot process arbitrary status update. Current status: " + ctx.getOrder().getStatus());
    }
}
