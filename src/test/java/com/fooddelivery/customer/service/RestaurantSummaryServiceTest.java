package com.fooddelivery.customer.service;

import com.fooddelivery.common.dto.ledger.BeneficiaryResponse;
import com.fooddelivery.common.dto.ledger.PayeeMoneySummaryDto;
import com.fooddelivery.common.dto.ledger.PayoutDto;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.customer.dto.RestaurantSummary;
import com.fooddelivery.customer.service.money.RestaurantSummaryService;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.repository.IOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

public class RestaurantSummaryServiceTest {

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private LedgerClient ledgerClient;

    @InjectMocks
    private RestaurantSummaryService restaurantSummaryService;

    private final UUID restaurantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetSummaryWithOrders() {
        Order order = new Order();
        order.setCreatedAt(LocalDateTime.now());
        order.setRestaurantPlatformFee(new BigDecimal("5.00"));
        order.setRestaurantDeliveryContribution(new BigDecimal("2.00"));
        order.setRestaurantPayout(new BigDecimal("43.00"));

        OrderItem item = new OrderItem();
        item.setPrice(new BigDecimal("25.00"));
        item.setQuantity(2);
        order.setOrderItems(Set.of(item));

        when(orderRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(order));
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId))
                .thenReturn(PayeeMoneySummaryDto.builder().unsettledAmount(BigDecimal.ZERO).build());

        RestaurantSummary summary = restaurantSummaryService.getSummary(restaurantId, "month");

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
        when(orderRepository.findByRestaurantId(restaurantId)).thenReturn(List.of());
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId)).thenReturn(PayeeMoneySummaryDto.builder()
                .unsettledAmount(new BigDecimal("8420.75"))
                .lastPayout(PayoutDto.builder()
                        .id(payoutId).amount(new BigDecimal("15000.00")).status("PAID")
                        .paidAt(OffsetDateTime.parse("2026-09-01T10:15:30Z")).build())
                .beneficiary(BeneficiaryResponse.builder()
                        .accountNumberMasked("XXXX1234").verified(true).build())
                .build());

        RestaurantSummary summary = restaurantSummaryService.getSummary(restaurantId, "month");

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
        when(orderRepository.findByRestaurantId(restaurantId)).thenReturn(List.of());
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId)).thenReturn(PayeeMoneySummaryDto.builder()
                .unsettledAmount(BigDecimal.ZERO)
                .beneficiary(BeneficiaryResponse.builder().accountNumberMasked("XXXX9999").verified(false).build())
                .build());

        RestaurantSummary summary = restaurantSummaryService.getSummary(restaurantId, "month");

        assertEquals("UNVERIFIED", summary.getBeneficiaryStatus().getVerificationStatus());
    }

    @Test
    void testGetSummaryLedgerClientFails() {
        when(orderRepository.findByRestaurantId(restaurantId)).thenReturn(List.of());
        when(ledgerClient.getPayeeSummary("RESTAURANT", restaurantId)).thenThrow(new RuntimeException("API error"));

        RestaurantSummary summary = restaurantSummaryService.getSummary(restaurantId, "month");

        assertEquals(0, summary.getOrders());
        assertNull(summary.getPendingBalance(), "an unavailable ledger must not be reported as a zero balance");
        assertNull(summary.getLastPayout());
    }
}
