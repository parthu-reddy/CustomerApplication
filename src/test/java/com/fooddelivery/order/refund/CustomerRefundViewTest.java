package com.fooddelivery.order.refund;

import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What the customer is shown about their refund.
 *
 * <p>Timestamps are {@link OffsetDateTime}: the columns behind them are {@code TIMESTAMPTZ}, and a
 * naive local time on a money row loses the offset. The entities carried {@code LocalDateTime} while
 * the columns were already {@code timestamptz}, which meant Hibernate refused to start under the
 * production {@code ddl-auto: validate}.
 */
public class CustomerRefundViewTest {

    @Test
    public void testView() {
        Instant requested = java.time.Instant.parse("2026-09-01T10:15:30Z");
        RefundView view = RefundView.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .status(RefundStatus.COMPLETED)
                .destination(RefundDestination.STORE_CREDIT)
                .method(PaymentMethod.WALLET)
                .requestedAt(requested)
                .expectedBy(requested.plus(java.time.Duration.ofDays(5)))
                .build();

        assertNotNull(view.getId());
        assertNotNull(view.getOrderId());
        assertEquals(new BigDecimal("50.00"), view.getAmount());
        assertEquals(RefundStatus.COMPLETED, view.getStatus());
        assertEquals(RefundDestination.STORE_CREDIT, view.getDestination());
        assertEquals(requested, view.getRequestedAt());
    }

    /**
     * A request made at 10:15:30 IST is the same moment as 04:45:30Z. The view holds that moment, not
     * a wall clock, so whatever zone it was written from, every reader agrees on when it happened.
     */
    @Test
    public void anIstRequestIsTheSameMomentInUtc() {
        Instant istRequest = java.time.OffsetDateTime.parse("2026-09-01T10:15:30+05:30").toInstant();
        RefundView view = RefundView.builder().requestedAt(istRequest).expectedBy(istRequest.plus(java.time.Duration.ofDays(5))).build();

        assertEquals(java.time.Instant.parse("2026-09-01T04:45:30Z"), view.getRequestedAt());
    }

    /** The customer is told when to expect the money: five days from the request. */
    @Test
    public void expectedByIsFiveDaysAfterTheRequest() {
        Instant requested = java.time.Instant.parse("2026-09-01T10:15:30Z");
        RefundView view = RefundView.builder().requestedAt(requested).expectedBy(requested.plus(java.time.Duration.ofDays(5))).build();

        assertEquals(requested.plus(java.time.Duration.ofDays(5)), view.getExpectedBy());
    }
}
