package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;

import java.math.BigDecimal;
import java.util.UUID;

public class OutForDeliveryState implements OrderState {

    @Override
    public void handleOrderDelivered(OrderContext ctx) {
        Order order = ctx.getOrder();
        
        String providedOtp = ctx.getEventPayload().path("deliveryOtp").asText(null);
        if (providedOtp == null || !providedOtp.equals(order.getOtp())) {
            throw new com.fooddelivery.order.service.state.IllegalStateTransitionException("Invalid or missing delivery OTP. Cannot transition to DELIVERED.");
        }
        
        order.setStatus(OrderStatus.DELIVERED);
        ctx.getActionService().saveOrder(order);
        
        // Ledger accounting
        BigDecimal total = order.getTotalAmount();
        BigDecimal restPayout = total.multiply(new BigDecimal("0.80"));
        BigDecimal driverPayout = new BigDecimal("50.00");
        
        UUID restTransferId = UUID.nameUUIDFromBytes(("REST_PAYOUT_" + order.getId()).getBytes());
        ctx.getActionService().recordLedgerTransaction(
                restTransferId, OrderActionService.PLATFORM_ACCOUNT_ID, AppConstants.ACCOUNT_TYPE_PLATFORM, 
                order.getRestaurantId(), AppConstants.ACCOUNT_TYPE_RESTAURANT, restPayout
        );
        
        if (order.getDeliveryExecutiveId() != null) {
            UUID driverTransferId = UUID.nameUUIDFromBytes(("DRIVER_PAYOUT_" + order.getId()).getBytes());
            ctx.getActionService().recordLedgerTransaction(
                    driverTransferId, OrderActionService.PLATFORM_ACCOUNT_ID, AppConstants.ACCOUNT_TYPE_PLATFORM, 
                    order.getDeliveryExecutiveId(), AppConstants.ACCOUNT_TYPE_DRIVER, driverPayout
            );
        }
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_DELIVERED);
    }

    @Override
    public void handleDeliveryFailed(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.DELIVERY_FAILED);
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.DELIVERY_FAILED);
        ctx.setRequiresRefund(true);
    }
}
