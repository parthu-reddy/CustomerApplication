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

    /**
     * Which external gateway holds the money, or null when no gateway is involved.
     *
     * <p>Distinct from {@link #paymentMethod}: CARD and UPI are how the customer chose to pay,
     * RAZORPAY is who is holding the result. Conflating the two is what made every wallet order
     * unpayable -- a wallet intent has no gateway, so routing on this field alone sent wallet
     * captures into the gateway branch with a null account id.
     */
    private final String gateway;

    /**
     * How the customer paid. The state machine routes on this and only this; the gateway is read
     * once the route is already known to need one.
     */
    private final com.fooddelivery.common.enums.PaymentMethod paymentMethod;

    private boolean requiresRefund = false;

    // No convenience constructor that defaults either field. One existed for the gateway, every
    // construction site took it, and bookPaymentCaptured hashed the string "null" for all of them.
    // Callers with no gateway pass null visibly.
}
