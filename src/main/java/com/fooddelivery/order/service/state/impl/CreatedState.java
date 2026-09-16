package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;

import java.util.UUID;

public class CreatedState implements OrderState {

    /**
     * Routes on how the customer paid, and nothing else.
     *
     * <p>The switch is exhaustive so that a new payment method fails to compile rather than falling
     * into a branch that takes no money.
     */
    @Override
    public void handlePaymentSuccess(OrderContext ctx) {
        Order order = ctx.getOrder();
        com.fooddelivery.common.enums.PaymentMethod method = ctx.getPaymentMethod();
        if (method == null) {
            // A data defect, not a state-transition error: deliberately not an
            // IllegalStateTransitionException, which the consumers catch and swallow.
            throw new IllegalStateException("Order " + order.getId() + " has no payment method");
        }

        // A switch *expression*, not a statement: only the expression form is exhaustiveness-checked
        // against the enum, so adding a fifth payment method is a compile error here rather than a
        // silently unhandled case. (A statement switch over an enum compiles happily with a missing
        // constant, which is how this was written the first time.)
        Runnable capture = switch (method) {
            case CARD, UPI -> () -> captureViaGateway(ctx, order);
            case WALLET -> () -> captureFromWallet(ctx, order);
        };
        capture.run();
    }

    private void captureViaGateway(OrderContext ctx, Order order) {
        order.setStatus(OrderStatus.PENDING_ACCEPTANCE);
        order.setPaymentStatus(PaymentIntentStatus.SUCCESS);
        ctx.getActionService().saveOrder(order);

        ctx.getLedgerBookkeeper().bookPaymentCaptured(order, ctx.getGateway());
        announcePaid(ctx, order);
    }

    private void captureFromWallet(OrderContext ctx, Order order) {
        order.setStatus(OrderStatus.PENDING_ACCEPTANCE);
        order.setPaymentStatus(PaymentIntentStatus.SUCCESS);
        ctx.getActionService().saveOrder(order);

        // The money moved inside WalletService before this event was published, so there is no
        // gateway to name -- but it did move, and the book has to say so.
        ctx.getLedgerBookkeeper().bookWalletCaptured(order);
        announcePaid(ctx, order);
    }

    private void announcePaid(OrderContext ctx, Order order) {
        ctx.getActionService().emitOrderPaidEvent(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.ORDER_PAID);
        ctx.getActionService().updatePaymentIntentStatus(order.getId(), PaymentIntentStatus.SUCCESS);
    }

    @Override
    public void handlePaymentFailure(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentIntentStatus.FAILED);
        ctx.getActionService().saveOrder(order);

        ctx.getActionService().emitOrderCancelledEvent(order.getId(), "Payment failed");
        ctx.getActionService().updatePaymentIntentStatus(order.getId(), PaymentIntentStatus.FAILED);
    }

    @Override
    public void cancelByCustomer(OrderContext ctx, String reason) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason != null ? reason : "Cancelled by customer");
        ctx.getActionService().saveOrder(order);

        // Emit the event to notify other services if needed
        ctx.getActionService().emitOrderCancelledByCustomerEvent(order.getId());
    }

    @Override
    public void handleOrderCancelledByAdmin(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        String reason = ((com.fooddelivery.common.event.OrderCancelledByAdminEvent) ctx.getEventPayload()).getReason(); order.setCancellationReason(reason != null ? reason : "Cancelled by Admin");
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.ORDER_CANCELLED_BY_ADMIN);
        
        if (order.getPaymentStatus() == PaymentIntentStatus.SUCCESS) {
            ctx.setRequiresRefund(true);
        }
    }
}
