package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
import com.fooddelivery.common.enums.AccountType;

import java.util.UUID;

public class CreatedState implements OrderState {

    @Override
    public void handlePaymentSuccess(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.PAID);
        ctx.getActionService().saveOrder(order);

        // Record initial payment from customer to platform
        UUID paymentTransferId = UUID.nameUUIDFromBytes(("PAYMENT_" + order.getId()).getBytes());
        ctx.getActionService().recordLedgerTransaction(
                paymentTransferId, 
                order.getCustomerId(), 
                AccountType.CUSTOMER, 
                OrderActionService.PLATFORM_ACCOUNT_ID, 
                AccountType.PLATFORM, 
                order.getTotalAmount()
            );

        ctx.getActionService().emitOrderPaidEvent(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_PAID.name());
        ctx.getActionService().updatePaymentIntentStatus(order.getId(), PaymentIntentStatus.SUCCESS);
    }

    @Override
    public void handlePaymentFailure(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
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
}
