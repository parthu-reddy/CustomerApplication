package com.fooddelivery.order.service.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.order.entity.Order;

public class OrderContext {
    private final Order order;
    private final JsonNode eventPayload;
    private final OrderActionService actionService;
    private boolean requiresRefund = false;

    public OrderContext(Order order, JsonNode eventPayload, OrderActionService actionService) {
        this.order = order;
        this.eventPayload = eventPayload;
        this.actionService = actionService;
    }

    @java.lang.SuppressWarnings("all")
    public Order getOrder() {
        return this.order;
    }

    @java.lang.SuppressWarnings("all")
    public JsonNode getEventPayload() {
        return this.eventPayload;
    }

    @java.lang.SuppressWarnings("all")
    public OrderActionService getActionService() {
        return this.actionService;
    }

    @java.lang.SuppressWarnings("all")
    public boolean isRequiresRefund() {
        return this.requiresRefund;
    }

    @java.lang.SuppressWarnings("all")
    public void setRequiresRefund(final boolean requiresRefund) {
        this.requiresRefund = requiresRefund;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof OrderContext)) return false;
        final OrderContext other = (OrderContext) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        if (this.isRequiresRefund() != other.isRequiresRefund()) return false;
        final java.lang.Object this$order = this.getOrder();
        final java.lang.Object other$order = other.getOrder();
        if (this$order == null ? other$order != null : !this$order.equals(other$order)) return false;
        final java.lang.Object this$eventPayload = this.getEventPayload();
        final java.lang.Object other$eventPayload = other.getEventPayload();
        if (this$eventPayload == null ? other$eventPayload != null : !this$eventPayload.equals(other$eventPayload)) return false;
        final java.lang.Object this$actionService = this.getActionService();
        final java.lang.Object other$actionService = other.getActionService();
        if (this$actionService == null ? other$actionService != null : !this$actionService.equals(other$actionService)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof OrderContext;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isRequiresRefund() ? 79 : 97);
        final java.lang.Object $order = this.getOrder();
        result = result * PRIME + ($order == null ? 43 : $order.hashCode());
        final java.lang.Object $eventPayload = this.getEventPayload();
        result = result * PRIME + ($eventPayload == null ? 43 : $eventPayload.hashCode());
        final java.lang.Object $actionService = this.getActionService();
        result = result * PRIME + ($actionService == null ? 43 : $actionService.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "OrderContext(order=" + this.getOrder() + ", eventPayload=" + this.getEventPayload() + ", actionService=" + this.getActionService() + ", requiresRefund=" + this.isRequiresRefund() + ")";
    }
}
