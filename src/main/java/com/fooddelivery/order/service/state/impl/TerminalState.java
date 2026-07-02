package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminalState implements OrderState {

    private static final Logger log = LoggerFactory.getLogger(TerminalState.class);

    @Override
    public void handlePaymentSuccess(OrderContext ctx) {
        Order order = ctx.getOrder();
        log.info("Order {} is in terminal state {}. Processing immediate refund for late payment success.", 
                order.getId(), order.getStatus());
        
        ctx.getActionService().updatePaymentIntentStatus(order.getId(), com.fooddelivery.common.constants.PaymentIntentStatus.SUCCESS);
        
        java.util.UUID paymentTransferId = java.util.UUID.nameUUIDFromBytes(("PAYMENT_" + order.getId()).getBytes());
        ctx.getActionService().recordLedgerTransaction(
                paymentTransferId, 
                order.getCustomerId(), 
                com.fooddelivery.common.constants.AppConstants.ACCOUNT_TYPE_CUSTOMER, 
                com.fooddelivery.order.service.state.OrderActionService.PLATFORM_ACCOUNT_ID, 
                com.fooddelivery.common.constants.AppConstants.ACCOUNT_TYPE_PLATFORM, 
                order.getTotalAmount()
        );
        
        if (order.getStatus() != com.fooddelivery.common.enums.OrderStatus.DELIVERED) {
            ctx.setRequiresRefund(true);
        }
    }

    @Override
    public void handlePaymentFailure(OrderContext ctx) {
        log.info("Ignoring payment failure for Order {} because it is already in terminal state {}", 
                ctx.getOrder().getId(), ctx.getOrder().getStatus());
    }

    @Override
    public void handleOrderAccepted(OrderContext ctx) {
        log.warn("Ignoring ORDER_ACCEPTED for Order {}. Already terminal: {}", ctx.getOrder().getId(), ctx.getOrder().getStatus());
    }

    @Override
    public void handleOrderReady(OrderContext ctx) {
        log.warn("Ignoring ORDER_READY for Order {}. Already terminal: {}", ctx.getOrder().getId(), ctx.getOrder().getStatus());
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
