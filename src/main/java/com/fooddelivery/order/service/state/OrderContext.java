package com.fooddelivery.order.service.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.order.entity.Order;




@lombok.Data
@lombok.RequiredArgsConstructor
@lombok.AllArgsConstructor
public class OrderContext {
    private final Order order;
    private final JsonNode eventPayload;
    private final OrderActionService actionService;
    private final com.fooddelivery.order.ledger.LedgerBookkeeper ledgerBookkeeper;
    private final String gateway;
    private boolean requiresRefund = false;

    public OrderContext(Order order, JsonNode eventPayload, OrderActionService actionService, com.fooddelivery.order.ledger.LedgerBookkeeper ledgerBookkeeper) {
        this(order, eventPayload, actionService, ledgerBookkeeper, null);
    }
}
