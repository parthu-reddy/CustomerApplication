package com.fooddelivery.order.service.state.impl;

import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
import java.util.UUID;
@lombok.extern.slf4j.Slf4j

public class HandedOverState implements OrderState {
    

    @Override
    public void handleOrderDelivered(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED);
        ctx.getActionService().saveOrder(order);
        if (ctx.getLedgerBookkeeper() != null) {
            ctx.getLedgerBookkeeper().bookDelivered(order);
            if (order.getPaymentMethod() == com.fooddelivery.common.enums.PaymentMethod.COD) {
                ctx.getLedgerBookkeeper().bookCashCollected(order);
                order.setPaymentStatus(com.fooddelivery.common.constants.PaymentIntentStatus.SUCCESS);
                // The gatewayOrderId for COD is set by PaymentGatewayOrchestrator, but payment is now successful.
                ctx.getActionService().updatePaymentIntentStatus(order.getId(), com.fooddelivery.common.constants.PaymentIntentStatus.SUCCESS);
            }
        }
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_DELIVERED.name());
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
    public void handleOrderCancelledByAdmin(OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(ctx.getEventPayload().path("reason").asText("Cancelled by Admin (Delivery abandoned or failed)"));
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), EventType.ORDER_CANCELLED_BY_ADMIN.name());
        ctx.setRequiresRefund(true);
    }
}
