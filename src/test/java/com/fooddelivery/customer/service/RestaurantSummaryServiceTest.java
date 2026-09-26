package com.fooddelivery.customer.service;

import com.fooddelivery.common.dto.ledger.BeneficiaryResponse;
import com.fooddelivery.common.dto.ledger.PayeeMoneySummaryDto;
import com.fooddelivery.common.dto.ledger.PayoutDto;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.customer.client.RestaurantClient;
import com.fooddelivery.customer.dto.RestaurantSummary;
import com.fooddelivery.customer.service.money.RestaurantSummaryService;
import com.fooddelivery.customer.service.money.SummaryPeriod;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.repository.IOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class RestaurantSummaryServiceTest {

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private LedgerClient ledgerClient;

    @Mock
    private RestaurantClient restaurantClient;

    private final UUID restaurantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private RestaurantSummaryService serviceAt(String now) {
        return new RestaurantSummaryService(orderRepository, ledgerClient, restaurantClient,
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }

    private void outletIn(String zone) {
        when(restaurantClient.getOutletSummary(restaurantId)).thenReturn(Map.of(
                "id", restaurantId.toString(), "name", "Outlet", "brandName", "Brand", "timeZone", zone));
    }

    private RestaurantSummaryService service() {
        outletIn("Asia/Kolkata");
        return serviceAt("2026-09-25T10:00:00Z");
    }

    @Test
    void testGetSummaryWithOrders() {
        Order order = new Order();
        order.setCreatedAt(Instant.now());
        order.setRestaurantPlatformFee(new BigDecimal("5.00"));
        order.setRestaurantDeliveryContribution(new BigDecimal("2.00"));
        order.setRestaurantPayout(new BigDecimal("43.00"));

        OrderItem item = new OrderItem();
        item.setPrice(new BigDecimal("25.00"));
        item.setQuantity(2);
        order.setOrderItems(Set.of(item));

        when(orderRepository.findByRestaurantIdDeliveredInWindow(eq(restaurantId), any(), any())).thenReturn(List.of(order));
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId))
                .thenReturn(PayeeMoneySummaryDto.builder().unsettledAmount(BigDecimal.ZERO).build());

        RestaurantSummary summary = service().getSummary(restaurantId, SummaryPeriod.MONTH);

        assertEquals(1, summary.getOrders());
        assertEquals(new BigDecimal("50.00"), summary.getGrossFoodCost());
        assertEquals(new BigDecimal("5.00"), summary.getPlatformFees());
        assertEquals(new BigDecimal("2.00"), summary.getDeliveryContribution());
        assertEquals(new BigDecimal("43.00"), summary.getNetEarnings());
    }

    /**
     * The pending balance, last payout and bank status are the ledger's answers, not zeros. They were
     * hardcoded behind a discarded call to the admin-only queue of every payee on the platform.
     */
    @Test
    void pendingBalanceAndLastPayoutComeFromTheLedger() {
        UUID payoutId = UUID.randomUUID();
        when(orderRepository.findByRestaurantIdDeliveredInWindow(eq(restaurantId), any(), any())).thenReturn(List.of());
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId)).thenReturn(PayeeMoneySummaryDto.builder()
                .unsettledAmount(new BigDecimal("8420.75"))
                .lastPayout(PayoutDto.builder()
                        .id(payoutId).amount(new BigDecimal("15000.00")).status("PAID")
                        .paidAt(Instant.parse("2026-09-01T10:15:30Z")).build())
                .beneficiary(BeneficiaryResponse.builder()
                        .accountNumberMasked("XXXX1234").verified(true).build())
                .build());

        RestaurantSummary summary = service().getSummary(restaurantId, SummaryPeriod.MONTH);

        assertEquals(new BigDecimal("8420.75"), summary.getPendingBalance());
        assertNotNull(summary.getLastPayout());
        assertEquals(payoutId.toString(), summary.getLastPayout().getPayoutId());
        assertEquals(new BigDecimal("15000.00"), summary.getLastPayout().getAmount());
        assertNotNull(summary.getBeneficiaryStatus());
        assertEquals("VERIFIED", summary.getBeneficiaryStatus().getVerificationStatus());
    }

    /** An unverified beneficiary must read as unverified, not as an empty object. */
    @Test
    void unverifiedBeneficiaryIsReportedAsSuch() {
        when(orderRepository.findByRestaurantIdDeliveredInWindow(eq(restaurantId), any(), any())).thenReturn(List.of());
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId)).thenReturn(PayeeMoneySummaryDto.builder()
                .unsettledAmount(BigDecimal.ZERO)
                .beneficiary(BeneficiaryResponse.builder().accountNumberMasked("XXXX9999").verified(false).build())
                .build());

        RestaurantSummary summary = service().getSummary(restaurantId, SummaryPeriod.MONTH);

        assertEquals("UNVERIFIED", summary.getBeneficiaryStatus().getVerificationStatus());
    }

    @Test
    void testGetSummaryLedgerClientFails() {
        when(orderRepository.findByRestaurantIdDeliveredInWindow(eq(restaurantId), any(), any())).thenReturn(List.of());
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId)).thenThrow(new RuntimeException("API error"));

        RestaurantSummary summary = service().getSummary(restaurantId, SummaryPeriod.MONTH);

        assertEquals(0, summary.getOrders());
        assertNull(summary.getPendingBalance(), "an unavailable ledger must not be reported as a zero balance");
        assertNull(summary.getLastPayout());
    }

    // ---- the period is the outlet's, read from its zone and the injected clock ----

    /** New York's fall-back day: 25 hours, from its own midnight. */
    @Test
    void todayIsTheOutletsDayAcrossTheFallBack() {
        outletIn("America/New_York");

        serviceAt("2026-11-01T15:00:00Z").getSummary(restaurantId, SummaryPeriod.TODAY);

        verify(orderRepository).findByRestaurantIdDeliveredInWindow(restaurantId,
                Instant.parse("2026-11-01T04:00:00Z"), Instant.parse("2026-11-02T05:00:00Z"));
    }

    /** London's week containing the fall-back: Monday 00:00 BST to Monday 00:00 GMT, 169 hours. */
    @Test
    void weekIsTheOutletsMondayToMondayAcrossTheFallBack() {
        outletIn("Europe/London");

        serviceAt("2026-10-21T12:00:00Z").getSummary(restaurantId, SummaryPeriod.WEEK);

        verify(orderRepository).findByRestaurantIdDeliveredInWindow(restaurantId,
                Instant.parse("2026-10-18T23:00:00Z"), Instant.parse("2026-10-26T00:00:00Z"));
    }

    /** New York's March loses an hour to the spring-forward. */
    @Test
    void monthIsTheOutletsCalendarMonthAcrossTheSpringForward() {
        outletIn("America/New_York");

        serviceAt("2026-03-20T12:00:00Z").getSummary(restaurantId, SummaryPeriod.MONTH);

        verify(orderRepository).findByRestaurantIdDeliveredInWindow(restaurantId,
                Instant.parse("2026-03-01T05:00:00Z"), Instant.parse("2026-04-01T04:00:00Z"));
    }

    /**
     * The same instant is a different day for two outlets: 01:30 on the 26th in Kolkata, 13:00 on the
     * 25th in Los Angeles. The old code summed one UTC month for both.
     */
    @Test
    void twoOutletsInDifferentZonesGetTheirOwnDay() {
        outletIn("America/Los_Angeles");
        serviceAt("2026-09-25T20:00:00Z").getSummary(restaurantId, SummaryPeriod.TODAY);
        verify(orderRepository).findByRestaurantIdDeliveredInWindow(restaurantId,
                Instant.parse("2026-09-25T07:00:00Z"), Instant.parse("2026-09-26T07:00:00Z"));

        outletIn("Asia/Kolkata");
        serviceAt("2026-09-25T20:00:00Z").getSummary(restaurantId, SummaryPeriod.TODAY);
        verify(orderRepository).findByRestaurantIdDeliveredInWindow(restaurantId,
                Instant.parse("2026-09-25T18:30:00Z"), Instant.parse("2026-09-26T18:30:00Z"));
    }

    // ---- no zone, no summary ----

    @Test
    void anUnreachableRestaurantServiceIsA503AndNothingIsSummed() {
        when(restaurantClient.getOutletSummary(restaurantId)).thenThrow(new IllegalStateException("fallback"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> serviceAt("2026-09-25T10:00:00Z").getSummary(restaurantId, SummaryPeriod.TODAY));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        verifyNoInteractions(orderRepository, ledgerClient);
    }

    @Test
    void anOutletWithoutAZoneIsA503() {
        when(restaurantClient.getOutletSummary(restaurantId)).thenReturn(Map.of("id", restaurantId.toString()));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> serviceAt("2026-09-25T10:00:00Z").getSummary(restaurantId, SummaryPeriod.TODAY));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        verify(orderRepository, never()).findByRestaurantIdDeliveredInWindow(any(), any(), any());
    }

    @Test
    void noOutletSummaryAtAllIsA503() {
        when(restaurantClient.getOutletSummary(restaurantId)).thenReturn(null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> serviceAt("2026-09-25T10:00:00Z").getSummary(restaurantId, SummaryPeriod.TODAY));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }

    /** Offsets and made-up names carry no daylight-saving rules, so they are not a zone to sum a day in. */
    @ParameterizedTest
    @ValueSource(strings = {"+05:30", "GMT+5", "IST", "Mars/Olympus", ""})
    void anOutletZoneThatIsNotAnIanaRegionIsA503(String zone) {
        Map<String, String> outlet = new HashMap<>();
        outlet.put("timeZone", zone);
        when(restaurantClient.getOutletSummary(restaurantId)).thenReturn(outlet);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> serviceAt("2026-09-25T10:00:00Z").getSummary(restaurantId, SummaryPeriod.TODAY));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        verifyNoInteractions(orderRepository, ledgerClient);
    }

    // ---- platform bonus and clawbacks: both were hardcoded to zero ----

    private static Order deliveredOrder(String food, String fee, String contribution, String bonus, String payout) {
        Order order = new Order();
        OrderItem item = new OrderItem();
        item.setPrice(new BigDecimal(food));
        item.setQuantity(1);
        order.setOrderItems(Set.of(item));
        order.setRestaurantPlatformFee(new BigDecimal(fee));
        order.setRestaurantDeliveryContribution(new BigDecimal(contribution));
        order.setPlatformBonus(new BigDecimal(bonus));
        order.setRestaurantPayout(new BigDecimal(payout));
        return order;
    }

    /**
     * The bonus is a per-order charge the outlet pays the platform, and restaurantPayout is already net
     * of it (DynamicPricingService). Summed, the deductions now account for the whole gap between gross
     * and net; with the bonus stuck at zero they did not.
     */
    @Test
    void platformBonusIsSummedFromTheDeliveredOrders() {
        // payout = food - fee - contribution - bonus, as DynamicPricingService computes it
        when(orderRepository.findByRestaurantIdDeliveredInWindow(eq(restaurantId), any(), any())).thenReturn(List.of(
                deliveredOrder("400.00", "10.00", "30.00", "4.50", "355.50"),
                deliveredOrder("250.00", "10.00", "25.00", "2.00", "213.00")));
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId)).thenReturn(PayeeMoneySummaryDto.builder().build());

        RestaurantSummary summary = service().getSummary(restaurantId, SummaryPeriod.MONTH);

        assertEquals(new BigDecimal("6.50"), summary.getPlatformBonus());
        assertEquals(summary.getNetEarnings(), summary.getGrossFoodCost()
                .subtract(summary.getPlatformFees()).subtract(summary.getDeliveryContribution()).subtract(summary.getPlatformBonus()));
    }

    /**
     * Clawbacks are the ledger's CLAWBACK debits on the outlet's payable, over the outlet's own period:
     * here New York's 25-hour fall-back day.
     */
    @Test
    void clawbacksAreTheLedgersForTheOutletsOwnWindow() {
        outletIn("America/New_York");
        when(orderRepository.findByRestaurantIdDeliveredInWindow(eq(restaurantId), any(), any())).thenReturn(List.of());
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId)).thenReturn(PayeeMoneySummaryDto.builder().build());
        when(ledgerClient.getCategoryTotal(LedgerAccountType.RESTAURANT_PAYABLE, restaurantId, ChargeCategory.CLAWBACK,
                TransactionDirection.DEBIT, Instant.parse("2026-11-01T04:00:00Z"), Instant.parse("2026-11-02T05:00:00Z")))
                .thenReturn(new BigDecimal("75.25"));

        RestaurantSummary summary = serviceAt("2026-11-01T15:00:00Z").getSummary(restaurantId, SummaryPeriod.TODAY);

        assertEquals(new BigDecimal("75.25"), summary.getClawbacks());
    }

    /** An unreachable ledger leaves the figure absent: a zero would read as "nothing was clawed back". */
    @Test
    void clawbacksTheLedgerCannotReportAreAbsentNotZero() {
        when(orderRepository.findByRestaurantIdDeliveredInWindow(eq(restaurantId), any(), any())).thenReturn(List.of());
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId)).thenReturn(PayeeMoneySummaryDto.builder()
                .unsettledAmount(new BigDecimal("8420.75")).build());
        when(ledgerClient.getCategoryTotal(any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("Ledger service is unavailable"));

        RestaurantSummary summary = service().getSummary(restaurantId, SummaryPeriod.MONTH);

        assertNull(summary.getClawbacks());
        assertEquals(new BigDecimal("8420.75"), summary.getPendingBalance(), "one ledger read failing must not blank the other");
    }

    @Test
    void anUnreachablePayeeSummaryDoesNotBlankTheClawbacks() {
        when(orderRepository.findByRestaurantIdDeliveredInWindow(eq(restaurantId), any(), any())).thenReturn(List.of());
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId)).thenThrow(new RuntimeException("API error"));
        when(ledgerClient.getCategoryTotal(any(), any(), any(), any(), any(), any())).thenReturn(new BigDecimal("12.00"));

        RestaurantSummary summary = service().getSummary(restaurantId, SummaryPeriod.MONTH);

        assertNull(summary.getPendingBalance());
        assertEquals(new BigDecimal("12.00"), summary.getClawbacks());
    }
}
