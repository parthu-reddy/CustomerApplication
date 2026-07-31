package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
import com.fooddelivery.common.enums.AccountType;

import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.order.service.state.OrderActionService;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReadyForPickupState implements OrderState {

    @Override
    public void handleDriverAssigned(OrderContext ctx) {
        Order order = ctx.getOrder();
        String driverIdStr = ctx.getEventPayload().path("driverId").asText(null);
        try {
            UUID driverUUID = UUID.fromString(driverIdStr);
            order.setDeliveryExecutiveId(driverUUID);
            order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.ASSIGNED);
            // Notice: We do NOT transition back to DISPATCHED if already READY_FOR_PICKUP
            ctx.getActionService().saveOrder(order);
            ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.DRIVER_ON_THE_WAY.name());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid UUID format for driverId: " + driverIdStr, e);
        }
    }

    @Override
    public void handleDriverAtRestaurant(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.AT_RESTAURANT);
        ctx.getActionService().saveOrder(order);
    }

    @Override
    public void handleOrderHandedOver(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.HANDED_OVER);
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.OUT_FOR_DELIVERY);
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.DRIVER_ON_THE_WAY.name());
    }

    @Override
    public void handleStatusUpdate(OrderContext ctx) {
        String updateStatus = ctx.getEventPayload().path("status").asText(null);
        if ("OUT_FOR_DELIVERY".equals(updateStatus)) {
            handleOrderHandedOver(ctx);
        } else {
            OrderState.super.handleStatusUpdate(ctx);
        }
    }

    @Override
    public void handleDispatchFailed(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.FAILED);
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.DISPATCH_FAILED.name());
    }

    @Override
    public void handleOrderCancelledByAdmin(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(ctx.getEventPayload().path("reason").asText("Cancelled by Admin"));
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_CANCELLED_BY_ADMIN.name());
        ctx.setRequiresRefund(true);
    }

    @Override
    public void handleManualInterventionRequired(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.MANUAL_INTERVENTION_REQUIRED);
        ctx.getActionService().saveOrder(order);
        log.info("Manual intervention required for order {}. Delivery status set to MANUAL_INTERVENTION_REQUIRED.", order.getId());
    }

    @Override
    public void handleDeliveryFailed(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.FAILED);
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.DELIVERY_FAILED.name());
        ctx.setRequiresRefund(true);
    }

    @Override
    public void handleOrderDelivered(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED);
        ctx.getActionService().saveOrder(order);
        
        // Ledger accounting
        if (order.getCharges() != null) {
            for (com.fooddelivery.order.entity.OrderCharge charge : order.getCharges()) {
                UUID fromId = getAccountId(charge.getPayerType(), order, false);
                AccountType fromType = getAccountType(charge.getPayerType());
                
                UUID toId = getAccountId(charge.getPayeeType(), order, true);
                AccountType toType = getAccountType(charge.getPayeeType());
                
                if (fromId != null && toId != null) {
                    UUID transferId = UUID.nameUUIDFromBytes(("CHARGE_" + charge.getId()).getBytes());
                    ctx.getActionService().recordLedgerTransaction(transferId, fromId, fromType, toId, toType, charge.getAmount(), charge.getCategory());
                }
            }
        }
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_DELIVERED.name());
    }
    
    private UUID getAccountId(com.fooddelivery.order.enums.ChargeEntityType type, Order order, boolean isPayee) {
        if (type == com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER) return OrderActionService.PLATFORM_ACCOUNT_ID;
        if (type == com.fooddelivery.order.enums.ChargeEntityType.PLATFORM) return isPayee ? OrderActionService.PLATFORM_PROFIT_ACCOUNT_ID : OrderActionService.PLATFORM_ACCOUNT_ID;
        if (type == com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT) return order.getRestaurantId();
        if (type == com.fooddelivery.order.enums.ChargeEntityType.DRIVER) return order.getDeliveryExecutiveId();
        return null;
    }
    
    private AccountType getAccountType(com.fooddelivery.order.enums.ChargeEntityType type) {
        if (type == com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER) return AccountType.PLATFORM;
        if (type == com.fooddelivery.order.enums.ChargeEntityType.PLATFORM) return AccountType.PLATFORM;
        if (type == com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT) return AccountType.RESTAURANT;
        if (type == com.fooddelivery.order.enums.ChargeEntityType.DRIVER) return AccountType.DRIVER;
        return AccountType.PLATFORM;
    }

    @Override
    public void handleOrderCancelledByRestaurant(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
        order.setCancellationReason(ctx.getEventPayload().path("reason").asText("Restaurant could not fulfill the order"));
        ctx.getActionService().saveOrder(order);
        
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_CANCELLED_BY_RESTAURANT.name());
        ctx.setRequiresRefund(true);
    }
}
