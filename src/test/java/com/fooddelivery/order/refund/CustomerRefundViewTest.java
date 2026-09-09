package com.fooddelivery.order.refund;

import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
        OffsetDateTime requested = OffsetDateTime.of(2026, 9, 1, 10, 15, 30, 0, ZoneOffset.UTC);
        RefundView view = RefundView.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .status(RefundStatus.COMPLETED)
                .destination(RefundDestination.STORE_CREDIT)
                .method(PaymentMethod.WALLET)
                .requestedAt(requested)
                .expectedBy(requested.plusDays(5))
                .build();

        assertNotNull(view.getId());
        assertNotNull(view.getOrderId());
        assertEquals(new BigDecimal("50.00"), view.getAmount());
        assertEquals(RefundStatus.COMPLETED, view.getStatus());
        assertEquals(RefundDestination.STORE_CREDIT, view.getDestination());
        assertEquals(requested, view.getRequestedAt());
    }

    /** The offset must survive the round trip; that is the whole point of the type. */
    @Test
    public void timestampsKeepTheirOffset() {
        OffsetDateTime istRequest = OffsetDateTime.of(2026, 9, 1, 10, 15, 30, 0, ZoneOffset.ofHoursMinutes(5, 30));
        RefundView view = RefundView.builder().requestedAt(istRequest).expectedBy(istRequest.plusDays(5)).build();

        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), view.getRequestedAt().getOffset());
        assertEquals(istRequest.toInstant(), view.getRequestedAt().toInstant());
    }

    /** The customer is told when to expect the money: five days from the request. */
    @Test
    public void expectedByIsFiveDaysAfterTheRequest() {
        OffsetDateTime requested = OffsetDateTime.of(2026, 9, 1, 10, 15, 30, 0, ZoneOffset.UTC);
        RefundView view = RefundView.builder().requestedAt(requested).expectedBy(requested.plusDays(5)).build();

        assertEquals(requested.plusDays(5), view.getExpectedBy());
    }
}
