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
        // Completed deliveries still have HANDED_OVER status. Late failure/cancellation and
        // duplicate delivery messages must not relabel them, refund them or book earnings again.
        if (ctx.getOrder().getDeliveredAt() != null
                || ctx.getOrder().getDeliveryStatus() == com.fooddelivery.common.enums.DeliveryStatus.DELIVERED) {
            log.info("Ignoring late delivery action for already delivered order {}", ctx.getOrder().getId());
            return;
        }
        ctx.getActionService().completeDelivery(ctx);
    }


    /**
     * The rider had the food and it did not arrive. Terminal, so the stuck-order sweeper does not
     * later cancel the same order a second time and attempt a second refund.
     */
    @Override
    public void handleDeliveryFailed(OrderContext ctx) {
        // Completed deliveries still have HANDED_OVER status. Late failure/cancellation and
        // duplicate delivery messages must not relabel them, refund them or book earnings again.
        if (ctx.getOrder().getDeliveredAt() != null
                || ctx.getOrder().getDeliveryStatus() == com.fooddelivery.common.enums.DeliveryStatus.DELIVERED) {
            log.info("Ignoring late delivery action for already delivered order {}", ctx.getOrder().getId());
            return;
        }
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.DELIVERY_FAILED);
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.FAILED);
        String reason = null; if (ctx.getEventPayload() instanceof com.fooddelivery.common.event.DeliveryFailedEvent) reason = ((com.fooddelivery.common.event.DeliveryFailedEvent) ctx.getEventPayload()).getReason(); else if (ctx.getEventPayload() instanceof com.fooddelivery.common.event.OrderStatusUpdatedEvent) reason = ((com.fooddelivery.common.event.OrderStatusUpdatedEvent) ctx.getEventPayload()).getReason(); order.setCancellationReason(reason != null ? reason : "Delivery failed");
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.DELIVERY_FAILED);
        ctx.setRequiresRefund(true);
    }

    @Override
    public void handleOrderCancelledByAdmin(OrderContext ctx) {
        // Completed deliveries still have HANDED_OVER status. Late failure/cancellation and
        // duplicate delivery messages must not relabel them, refund them or book earnings again.
        if (ctx.getOrder().getDeliveredAt() != null
                || ctx.getOrder().getDeliveryStatus() == com.fooddelivery.common.enums.DeliveryStatus.DELIVERED) {
            log.info("Ignoring late delivery action for already delivered order {}", ctx.getOrder().getId());
            return;
        }
        Order order = ctx.getOrder();
        order.setStatus(OrderStatus.CANCELLED);
        String reason = ((com.fooddelivery.common.event.OrderCancelledByAdminEvent) ctx.getEventPayload()).getReason(); order.setCancellationReason(reason != null ? reason : "Cancelled by Admin (Delivery abandoned or failed)");
        ctx.getActionService().saveOrder(order);
        ctx.getActionService().sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.ORDER_CANCELLED_BY_ADMIN);
        ctx.setRequiresRefund(true);
    }
}
