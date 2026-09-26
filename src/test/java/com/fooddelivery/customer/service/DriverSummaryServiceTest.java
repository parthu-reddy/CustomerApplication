package com.fooddelivery.customer.service;

import com.fooddelivery.common.dto.ledger.PayeeMoneySummaryDto;
import com.fooddelivery.common.dto.ledger.PayoutDto;
import com.fooddelivery.common.time.TimeWindow;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.customer.dto.DriverSummary;
import com.fooddelivery.customer.service.money.DriverSummaryService;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DriverSummaryServiceTest {

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private LedgerClient ledgerClient;

    @InjectMocks
    private DriverSummaryService driverSummaryService;

    private final UUID driverId = UUID.randomUUID();

    /** September on a Kolkata rider's calendar, as their browser sends it. */
    private static final TimeWindow SEPTEMBER_IN_KOLKATA =
            new TimeWindow(Instant.parse("2026-08-31T18:30:00Z"), Instant.parse("2026-09-30T18:30:00Z"));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private void noOrders() {
        when(orderRepository.findByDeliveryExecutiveIdDeliveredInWindow(eq(driverId), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void testGetSummaryWithOrders() {
        Order order = new Order();
        order.setDriverGrossPayout(new BigDecimal("150.00"));
        order.setDriverTaxes(new BigDecimal("10.00"));
        order.setDriverNetPayout(new BigDecimal("140.00"));

        when(orderRepository.findByDeliveryExecutiveIdDeliveredInWindow(eq(driverId), any(), any()))
                .thenReturn(List.of(order));
        when(ledgerClient.getPayeeSummary("DRIVER", driverId)).thenReturn(PayeeMoneySummaryDto.builder()
                .unsettledAmount(BigDecimal.ZERO).build());

        DriverSummary summary = driverSummaryService.getSummary(driverId, SEPTEMBER_IN_KOLKATA);

        assertEquals(1, summary.getDeliveries());
        assertEquals(new BigDecimal("150.00"), summary.getGross());
        assertEquals(new BigDecimal("10.00"), summary.getTaxes());
        assertEquals(new BigDecimal("140.00"), summary.getNet());
    }

    @Test
    void tipsAreTheirOwnLine_andAddToGrossAndNetUntaxed() {
        Order order = new Order();
        order.setDriverGrossPayout(new BigDecimal("150.00"));
        order.setDriverTaxes(new BigDecimal("10.00"));
        order.setDriverNetPayout(new BigDecimal("140.00"));
        order.setTipAmount(new BigDecimal("20.00"));
        when(orderRepository.findByDeliveryExecutiveIdDeliveredInWindow(eq(driverId), any(), any()))
                .thenReturn(List.of(order));
        when(ledgerClient.getPayeeSummary("DRIVER", driverId)).thenReturn(PayeeMoneySummaryDto.builder()
                .unsettledAmount(BigDecimal.ZERO).build());

        DriverSummary summary = driverSummaryService.getSummary(driverId, SEPTEMBER_IN_KOLKATA);

        assertEquals(new BigDecimal("20.00"), summary.getTips());
        assertEquals(new BigDecimal("170.00"), summary.getGross());
        assertEquals(new BigDecimal("10.00"), summary.getTaxes());
        assertEquals(new BigDecimal("160.00"), summary.getNet());
    }

    @Test
    void pendingBalanceAndLastPayoutComeFromTheLedger() {
        noOrders();
        UUID payoutId = UUID.randomUUID();
        when(ledgerClient.getPayeeSummary("DRIVER", driverId)).thenReturn(PayeeMoneySummaryDto.builder()
                .unsettledAmount(new BigDecimal("1240.50"))
                .lastPayout(PayoutDto.builder()
                        .id(payoutId).amount(new BigDecimal("3000.00")).status("PAID")
                        .paidAt(Instant.parse("2026-09-01T10:15:30Z")).build())
                .build());

        DriverSummary summary = driverSummaryService.getSummary(driverId, SEPTEMBER_IN_KOLKATA);

        assertEquals(new BigDecimal("1240.50"), summary.getPendingBalance());
        assertNotNull(summary.getLastPayout());
        assertEquals(payoutId.toString(), summary.getLastPayout().getPayoutId());
        assertEquals(new BigDecimal("3000.00"), summary.getLastPayout().getAmount());
        assertEquals("PAID", summary.getLastPayout().getStatus());
    }

    /**
     * An unreachable ledger must degrade rather than 500 -- and must leave the figures absent rather
     * than reporting a confident zero the rider would read as "you owe nothing".
     */
    @Test
    void testGetSummaryLedgerClientFails() {
        noOrders();
        when(ledgerClient.getPayeeSummary("DRIVER", driverId)).thenThrow(new RuntimeException("API error"));

        DriverSummary summary = driverSummaryService.getSummary(driverId, SEPTEMBER_IN_KOLKATA);

        assertEquals(0, summary.getDeliveries());
        assertNull(summary.getPendingBalance());
        assertNull(summary.getLastPayout());
    }

    // ---- delivered in the rider's window, used exactly as sent ----

    /**
     * New York's fall-back day, 25 hours, as a New York rider's browser sends it, asked of the
     * delivered-orders query. The old code ignored the request, summed one UTC month back from now,
     * and counted cancelled orders, whose quoted payouts the ledger never pays.
     */
    @Test
    void theRidersWindowReachesTheDeliveredQueryUnchanged() {
        noOrders();
        when(ledgerClient.getPayeeSummary("DRIVER", driverId)).thenReturn(PayeeMoneySummaryDto.builder().build());
        TimeWindow fallBackDay = new TimeWindow(Instant.parse("2026-11-01T04:00:00Z"), Instant.parse("2026-11-02T05:00:00Z"));

        driverSummaryService.getSummary(driverId, fallBackDay);

        verify(orderRepository).findByDeliveryExecutiveIdDeliveredInWindow(driverId,
                Instant.parse("2026-11-01T04:00:00Z"), Instant.parse("2026-11-02T05:00:00Z"));
        verify(orderRepository, org.mockito.Mockito.never()).findHistoryOrdersForDriver(any(), any(), any(), any(), any(), any());
    }
}
