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

public class DispatchedState implements OrderState {

    @Override
    public void handleOrderReady(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.NOTIFY_ORDER_READY_FOR_PICKUP);
    }

    @Override
    public void handleDriverAssigned(OrderContext ctx) {
        Order order = ctx.getOrder();
        String driverIdStr = ctx.getEventPayload().path("driverId").asText(null);
        try {
            UUID driverUUID = UUID.fromString(driverIdStr);
            order.setDeliveryExecutiveId(driverUUID);
            ctx.getActionService().saveOrder(order);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid UUID format for driverId: " + driverIdStr, e);
        }
    }

    @Override
    public void handleStatusUpdate(OrderContext ctx) {
        String updateStatus = ctx.getEventPayload().path("status").asText(null);
        if (OrderStatus.OUT_FOR_DELIVERY.name().equals(updateStatus) || OrderStatus.DISPATCHED.name().equals(updateStatus)) {
            Order order = ctx.getOrder();
            
            String providedOtp = ctx.getEventPayload().path("pickupOtp").asText(null);
            if (providedOtp == null || !providedOtp.equals(order.getPickupOtp())) {
                throw new com.fooddelivery.order.service.state.IllegalStateTransitionException("Invalid or missing pickup OTP. Cannot transition to OUT_FOR_DELIVERY.");
            }
            
            order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
            ctx.getActionService().saveOrder(order);
            ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.NOTIFY_DRIVER_ON_THE_WAY);
        } else {
            OrderState.super.handleStatusUpdate(ctx);
        }
    }

    @Override
    public void handleOrderDelivered(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.DELIVERED);
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
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_DELIVERED);
    }

    @Override
    public void handleDispatchFailed(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.DELIVERY_FAILED);
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.DISPATCH_FAILED);
        ctx.setRequiresRefund(true);
    }

    @Override
    public void handleDeliveryFailed(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.DELIVERY_FAILED);
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.DELIVERY_FAILED);
        ctx.setRequiresRefund(true);
    }

    @Override
    public void handleOrderCancelledByRestaurant(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
        order.setCancellationReason(ctx.getEventPayload().path("reason").asText("Restaurant could not fulfill the order"));
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_CANCELLED_BY_RESTAURANT);
        ctx.setRequiresRefund(true);
    }
}
