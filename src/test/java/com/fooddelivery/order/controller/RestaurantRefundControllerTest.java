package com.fooddelivery.order.controller;

import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The refund requests an outlet is answerable for.
 *
 * <p>This class was a placeholder asserting {@code true} with a comment saying the controller "does
 * not exist as a separate class". It did exist -- {@code RestaurantSupportTicketController}, at
 * {@code /api/v1/internal/restaurants/outlets/{outletId}/refund-requests}.
 *
 * <p>That path could never be reached from a browser: the gateway 403s external calls to
 * {@code /api/v1/internal/**} outside the admin carve-out, and the endpoint was guarded by role
 * alone with no ownership check. On 2026-09-09 it was folded into {@link RestaurantMoneyController}
 * at {@code /api/v1/money/restaurant/{outletId}/refund-requests} -- routed, and owner-scoped by
 * MoneyAccessPolicy. The three behaviours below moved with it.
 */
@ExtendWith(MockitoExtension.class)
public class RestaurantRefundControllerTest {

    @Mock
    private RefundRepository refundRepository;
    @Mock
    private com.fooddelivery.order.repository.IOrderRepository orderRepository;
    @Mock
    private com.fooddelivery.common.security.money.MoneyAccessPolicy moneyAccessPolicy;
    @Mock
    private com.fooddelivery.customer.service.money.RestaurantSummaryService restaurantSummaryService;
    @Mock
    private com.fooddelivery.customer.client.LedgerClient ledgerClient;

    private MockMvc mockMvc;
    private final UUID outletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Standalone: no security filter, so this exercises the handler's body. The
        // @PreAuthorize ownership check on the endpoint is covered by MoneyAccessPolicyTest.
        mockMvc = MockMvcBuilders.standaloneSetup(new com.fooddelivery.money.controller.RestaurantMoneyController(
                orderRepository, moneyAccessPolicy, refundRepository, restaurantSummaryService, ledgerClient)).build();
    }

    private Refund processingRefund() {
        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setOrderId(UUID.randomUUID());
        refund.setAmount(new BigDecimal("120.50"));
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setDestination(RefundDestination.ORIGINAL_METHOD);
        refund.setReasonCode("ITEM_MISSING");
        refund.setCreatedAt(OffsetDateTime.parse("2026-09-01T10:15:30Z"));
        refund.setUpdatedAt(OffsetDateTime.parse("2026-09-02T11:00:00Z"));
        return refund;
    }

    @Test
    void listsTheOutletsActiveRefundRequests() throws Exception {
        Refund refund = processingRefund();
        when(refundRepository.findRestaurantFaultRefunds(eq(outletId), eq(RefundStatus.PROCESSING)))
                .thenReturn(List.of(refund));

        mockMvc.perform(get("/api/v1/money/restaurant/" + outletId + "/refund-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(refund.getId().toString()))
                // Rupees as the API stores them, not paise.
                .andExpect(jsonPath("$[0].amount").value(120.50))
                .andExpect(jsonPath("$[0].reasonCode").value("ITEM_MISSING"));
    }

    /**
     * A PROCESSING refund has not completed. Reporting the last touch as a completion time told the
     * outlet money had moved when it had not.
     */
    @Test
    void aProcessingRefundHasNoCompletionTime() throws Exception {
        Refund refund = processingRefund();
        when(refundRepository.findRestaurantFaultRefunds(eq(outletId), eq(RefundStatus.PROCESSING)))
                .thenReturn(List.of(refund));

        mockMvc.perform(get("/api/v1/money/restaurant/" + outletId + "/refund-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestedAt").exists())
                .andExpect(jsonPath("$[0].completedAt").doesNotExist());
    }

    /** Only this outlet's fault refunds: the query is scoped, and the response must be too. */
    @Test
    void anOutletWithNoActiveRequestsGetsAnEmptyList() throws Exception {
        when(refundRepository.findRestaurantFaultRefunds(eq(outletId), eq(RefundStatus.PROCESSING)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/money/restaurant/" + outletId + "/refund-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
