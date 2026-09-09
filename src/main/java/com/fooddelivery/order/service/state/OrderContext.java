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

    // No 4-argument convenience constructor: it defaulted the gateway to null, and every construction
    // site took it, so bookPaymentCaptured hashed the string "null". Callers with no gateway now pass
    // null visibly, and booking a capture with one fails loudly.
}
