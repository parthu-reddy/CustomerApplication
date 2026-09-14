package com.fooddelivery.order.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.impl.TerminalState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Money captured for an order that will not be delivered always goes back.
 *
 * <p>The decision used to be `!"Cancelled by customer".equals(order.getCancellationReason())` — a
 * money outcome chosen by free text. Two identical situations refunded or did not depending on the
 * exact wording, and the wording the customer-facing path actually passes is
 * "Cancelled by customer via UI", so the branch that kept the money fired only for internal
 * cancellations.
 */
class LateCaptureAlwaysRefundsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrderContext ctxFor(OrderStatus status, String reason, LedgerBookkeeper bookkeeper) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCustomerId(UUID.randomUUID());
        o.setTotalAmount(new BigDecimal("420.00"));
        o.setPaymentMethod(PaymentMethod.CARD);
        o.setStatus(status);
        o.setCancellationReason(reason);
        return new OrderContext(o, null /* no order event on the payment path; no state here reads it */, mock(OrderActionService.class),
                bookkeeper, "RAZORPAY", o.getPaymentMethod());
    }

    @Test
    void theExactStringThatUsedToSuppressTheRefundNoLongerDoes() {
        OrderContext ctx = ctxFor(OrderStatus.CANCELLED, "Cancelled by customer", mock(LedgerBookkeeper.class));

        new TerminalState().handlePaymentSuccess(ctx);

        assertTrue(ctx.isRequiresRefund(),
                "the capture happened; withholding the refund keeps the customer's money");
    }

    @Test
    void everyTerminalStatusRefundsALateCapture() {
        for (OrderStatus terminal : java.util.Arrays.stream(OrderStatus.values())
                .filter(OrderStatus::isTerminal).toList()) {
            OrderContext ctx = ctxFor(terminal, "any wording at all", mock(LedgerBookkeeper.class));

            new TerminalState().handlePaymentSuccess(ctx);

            assertTrue(ctx.isRequiresRefund(), terminal + " must refund a late capture");
        }
    }

    @Test
    void aNullCancellationReasonRefundsToo() {
        OrderContext ctx = ctxFor(OrderStatus.CANCELLED, null, mock(LedgerBookkeeper.class));

        new TerminalState().handlePaymentSuccess(ctx);

        assertTrue(ctx.isRequiresRefund());
    }

    @Test
    void theCaptureIsStillBookedBeforeTheRefundIsRaised() {
        LedgerBookkeeper bookkeeper = mock(LedgerBookkeeper.class);
        OrderContext ctx = ctxFor(OrderStatus.CANCELLED_BY_PLATFORM, "no driver", bookkeeper);

        new TerminalState().handlePaymentSuccess(ctx);

        verify(bookkeeper).bookPaymentCaptured(ctx.getOrder(), "RAZORPAY");
        assertTrue(ctx.isRequiresRefund());
    }

    /** The status the order ended with is the record of who was at fault; it must survive. */
    @Test
    void aLateCaptureDoesNotRelabelTheTerminalStatus() {
        OrderContext ctx = ctxFor(OrderStatus.CANCELLED_BY_RESTAURANT, "out of stock", mock(LedgerBookkeeper.class));

        new TerminalState().handlePaymentSuccess(ctx);

        assertEquals(OrderStatus.CANCELLED_BY_RESTAURANT, ctx.getOrder().getStatus(),
                "forcing CANCELLED here would lose the fault that decides the clawback");
    }
}
