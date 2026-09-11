package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
import com.fooddelivery.order.service.state.OrderActionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@lombok.extern.slf4j.Slf4j

public class TerminalState implements OrderState {

    @Override
    public void handlePaymentSuccess(OrderContext ctx) {
        Order order = ctx.getOrder();
        log.info("Order {} is in terminal state {}. Processing immediate refund for late payment success.", 
                order.getId(), order.getStatus());
        
        ctx.getActionService().updatePaymentIntentStatus(order.getId(), com.fooddelivery.common.constants.PaymentIntentStatus.SUCCESS);
        
        if (ctx.getLedgerBookkeeper() != null) {
            // A payment landing after the order is already terminal is still a real capture and must
            // be booked -- but against the account that actually holds it. Routing this on the
            // gateway alone made a late wallet capture throw here for the same reason CreatedState
            // did: a wallet intent has no gateway name.
            bookLateCapture(ctx, order);
        }
        
        // The status is deliberately not touched. This state is only ever reached for an order that
        // is already terminal -- OrderStateFactory maps HANDED_OVER to HandedOverState -- so the
        // code that used to force CANCELLED here was overwriting the real reason the order ended,
        // and the two HANDED_OVER comparisons around it were unreachable. Relabelling a
        // CANCELLED_BY_RESTAURANT order as CANCELLED loses the fault that decides the clawback,
        // and Order.setStatus now refuses it outright.

        // Money was taken for an order that will not be delivered, so it goes back. Unconditionally.
        //
        // This used to read `if (!"Cancelled by customer".equals(order.getCancellationReason()))`,
        // which decided a money question on free text: the same situation refunded or did not
        // depending on whether the cancellation reason happened to be that exact string. It was
        // not, in practice -- CustomerOrderService passes "Cancelled by customer via UI" -- so the
        // branch that kept the money fired only for cancellations raised internally.
        ctx.setRequiresRefund(true);
    }

    /** Same routing as {@link CreatedState}: the account depends on the method, not the gateway. */
    private void bookLateCapture(OrderContext ctx, Order order) {
        com.fooddelivery.common.enums.PaymentMethod method = ctx.getPaymentMethod();
        if (method == null) {
            throw new IllegalStateException("Order " + order.getId() + " has no payment method");
        }
        // Switch expression for the same reason as CreatedState: a statement switch over an enum is
        // not exhaustiveness-checked, so a new payment method would silently book nothing here.
        Runnable book = switch (method) {
            case CARD, UPI -> () -> ctx.getLedgerBookkeeper().bookPaymentCaptured(order, ctx.getGateway());
            case WALLET -> () -> ctx.getLedgerBookkeeper().bookWalletCaptured(order);
            // Cash cannot arrive late through a payment event -- it is booked when the rider
            // delivers, by HandedOverState.
            case COD -> () -> log.info("Ignoring a payment event for COD order {}: cash is booked on delivery",
                    order.getId());
        };
        book.run();
    }

    @Override
    public void handlePaymentFailure(OrderContext ctx) {
        log.info("Ignoring payment failure for Order {} because it is already in terminal state {}",
                ctx.getOrder().getId(), ctx.getOrder().getStatus());
    }



    @Override
    public void handleDriverAssigned(OrderContext ctx) {
        log.warn("Ignoring DRIVER_ASSIGNED for Order {}. Already terminal: {}", ctx.getOrder().getId(), ctx.getOrder().getStatus());
    }

    @Override
    public void handleOrderDelivered(OrderContext ctx) {
        log.warn("Ignoring ORDER_DELIVERED for Order {}. Already terminal: {}", ctx.getOrder().getId(), ctx.getOrder().getStatus());
    }

    @Override
    public void handleDelayApprovalRequested(OrderContext ctx) {
        log.warn("Ignoring ORDER_DELAY_APPROVAL_REQUESTED for Order {}. Already terminal: {}", ctx.getOrder().getId(), ctx.getOrder().getStatus());
    }

    @Override
    public void handleDelayRejected(OrderContext ctx) {
        log.warn("Ignoring ORDER_DELAY_REJECTED for Order {}. Already terminal: {}", ctx.getOrder().getId(), ctx.getOrder().getStatus());
    }

    @Override
    public void handleOrderCancelledByRestaurant(OrderContext ctx) {
        log.warn("Ignoring ORDER_CANCELLED_BY_RESTAURANT for Order {}. Already terminal: {}", ctx.getOrder().getId(), ctx.getOrder().getStatus());
    }

    @Override
    public void handleDispatchFailed(OrderContext ctx) {
        log.warn("Ignoring DISPATCH_FAILED for Order {}. Already terminal: {}", ctx.getOrder().getId(), ctx.getOrder().getStatus());
    }

    @Override
    public void handleDeliveryFailed(OrderContext ctx) {
        log.warn("Ignoring DELIVERY_FAILED for Order {}. Already terminal: {}", ctx.getOrder().getId(), ctx.getOrder().getStatus());
    }

    @Override
    public void handleStatusUpdate(OrderContext ctx) {
        log.warn("Ignoring arbitrary status update for Order {}. Already terminal: {}", ctx.getOrder().getId(), ctx.getOrder().getStatus());
    }
}
