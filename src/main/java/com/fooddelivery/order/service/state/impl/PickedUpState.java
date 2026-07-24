package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
import com.fooddelivery.common.enums.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

public class PickedUpState implements OrderState {

    @Override
    public void handleOrderDelivered(OrderContext ctx) {
        Order order = ctx.getOrder();
        
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED);
        ctx.getActionService().saveOrder(order);
        
        // Ledger accounting
        BigDecimal total = order.getTotalAmount();
        BigDecimal restPayout = total.multiply(new BigDecimal("0.80"));
        BigDecimal driverPayout = new BigDecimal("50.00");
        
        UUID restTransferId = UUID.nameUUIDFromBytes(("REST_PAYOUT_" + order.getId()).getBytes());
        ctx.getActionService().recordLedgerTransaction(
                restTransferId, OrderActionService.PLATFORM_ACCOUNT_ID, AccountType.PLATFORM, 
                order.getRestaurantId(), AccountType.RESTAURANT, restPayout
        );
        
        if (order.getDeliveryExecutiveId() != null) {
            UUID driverTransferId = UUID.nameUUIDFromBytes(("DRIVER_PAYOUT_" + order.getId()).getBytes());
            ctx.getActionService().recordLedgerTransaction(
                    driverTransferId, OrderActionService.PLATFORM_ACCOUNT_ID, AccountType.PLATFORM, 
                    order.getDeliveryExecutiveId(), AccountType.DRIVER, driverPayout
            );
        }
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_DELIVERED.name());
    }

    @Override
    public void handleDeliveryFailed(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.DELIVERY_FAILED);
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.FAILED);
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.DELIVERY_FAILED.name());
        ctx.setRequiresRefund(true);
    }
}
