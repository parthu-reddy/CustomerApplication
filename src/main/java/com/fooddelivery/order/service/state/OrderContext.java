package com.fooddelivery.order.service.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.order.entity.Order;
import lombok.Data;

@Data
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
}
