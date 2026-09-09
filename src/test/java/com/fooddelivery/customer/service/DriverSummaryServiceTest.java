package com.fooddelivery.customer.service;

import com.fooddelivery.common.dto.ledger.CashSummaryDto;
import com.fooddelivery.common.dto.ledger.PayeeMoneySummaryDto;
import com.fooddelivery.common.dto.ledger.PayoutDto;
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
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class DriverSummaryServiceTest {

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private LedgerClient ledgerClient;

    @InjectMocks
    private DriverSummaryService driverSummaryService;

    private final UUID driverId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private void noOrders() {
        when(orderRepository.findHistoryOrdersForDriver(eq(driverId), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void testGetSummaryWithOrders() {
        Order order = new Order();
        order.setDriverGrossPayout(new BigDecimal("150.00"));
        order.setDriverTaxes(new BigDecimal("10.00"));
        order.setDriverNetPayout(new BigDecimal("140.00"));

        when(orderRepository.findHistoryOrdersForDriver(eq(driverId), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(ledgerClient.getCashSummary(driverId)).thenReturn(CashSummaryDto.builder()
                .driverId(driverId).cashCollected(BigDecimal.ZERO).cashRemitted(BigDecimal.ZERO)
                .cashInHand(BigDecimal.ZERO).build());
        when(ledgerClient.getPayeeSummary("DRIVER", driverId)).thenReturn(PayeeMoneySummaryDto.builder()
                .unsettledAmount(BigDecimal.ZERO).build());

        DriverSummary summary = driverSummaryService.getSummary(driverId, "month");

        assertEquals(1, summary.getDeliveries());
        assertEquals(new BigDecimal("150.00"), summary.getGross());
        assertEquals(new BigDecimal("10.00"), summary.getTaxes());
        assertEquals(new BigDecimal("140.00"), summary.getNet());
    }

    /**
     * Cash in hand comes from the ledger. It used to be hardcoded to zero behind a discarded call to
     * an admin-only endpoint, so a rider carrying money was always shown nothing.
     */
    @Test
    void cashFiguresComeFromTheLedgerNotFromZero() {
        noOrders();
        when(ledgerClient.getCashSummary(driverId)).thenReturn(CashSummaryDto.builder()
                .driverId(driverId)
                .cashCollected(new BigDecimal("900.00"))
                .cashRemitted(new BigDecimal("550.00"))
                .cashInHand(new BigDecimal("350.00"))
                .build());
        when(ledgerClient.getPayeeSummary("DRIVER", driverId)).thenReturn(PayeeMoneySummaryDto.builder()
                .unsettledAmount(BigDecimal.ZERO).build());

        DriverSummary summary = driverSummaryService.getSummary(driverId, "month");

        assertEquals(new BigDecimal("900.00"), summary.getCashCollected());
        assertEquals(new BigDecimal("550.00"), summary.getCashRemitted());
        assertEquals(new BigDecimal("350.00"), summary.getCashInHand());
    }

    @Test
    void pendingBalanceAndLastPayoutComeFromTheLedger() {
        noOrders();
        UUID payoutId = UUID.randomUUID();
        when(ledgerClient.getCashSummary(driverId)).thenReturn(CashSummaryDto.builder()
                .cashCollected(BigDecimal.ZERO).cashRemitted(BigDecimal.ZERO).cashInHand(BigDecimal.ZERO).build());
        when(ledgerClient.getPayeeSummary("DRIVER", driverId)).thenReturn(PayeeMoneySummaryDto.builder()
                .unsettledAmount(new BigDecimal("1240.50"))
                .lastPayout(PayoutDto.builder()
                        .id(payoutId).amount(new BigDecimal("3000.00")).status("PAID")
                        .paidAt(OffsetDateTime.parse("2026-09-01T10:15:30Z")).build())
                .build());

        DriverSummary summary = driverSummaryService.getSummary(driverId, "month");

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
        when(ledgerClient.getCashSummary(driverId)).thenThrow(new RuntimeException("API error"));
        when(ledgerClient.getPayeeSummary("DRIVER", driverId)).thenThrow(new RuntimeException("API error"));

        DriverSummary summary = driverSummaryService.getSummary(driverId, "month");

        assertEquals(0, summary.getDeliveries());
        assertNull(summary.getPendingBalance());
        assertNull(summary.getCashInHand());
        assertNull(summary.getLastPayout());
    }
}
