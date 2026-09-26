package com.fooddelivery.customer.controller;

import com.fooddelivery.money.controller.DriverMoneyController;

import com.fooddelivery.common.security.money.MoneyAccessPolicy;
import com.fooddelivery.common.security.money.MoneyOwnerType;
import com.fooddelivery.common.time.TimeWindow;
import com.fooddelivery.customer.dto.DriverSummary;
import com.fooddelivery.customer.service.money.DriverSummaryService;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class DriverMoneyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private MoneyAccessPolicy moneyAccessPolicy;

    @Mock
    private DriverSummaryService driverSummaryService;

    private final UUID driverId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private Order testOrder;

    @BeforeEach
    void setUp() {
        DriverMoneyController controller = new DriverMoneyController(orderRepository, moneyAccessPolicy, driverSummaryService, org.mockito.Mockito.mock(com.fooddelivery.customer.client.LedgerClient.class));
        // The platform's advice, so a bad window answers with the status production gives it.
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.fooddelivery.common.exception.GlobalExceptionHandler()).build();

        testOrder = new Order();
        testOrder.setId(orderId);
        testOrder.setDeliveryExecutiveId(driverId);
        testOrder.setDriverGrossPayout(BigDecimal.valueOf(25));
        testOrder.setDriverTaxes(BigDecimal.valueOf(10));
        testOrder.setDriverNetPayout(BigDecimal.valueOf(35));
        testOrder.setDeliveryFee(BigDecimal.valueOf(5));
        testOrder.setRestaurantDeliveryContribution(BigDecimal.valueOf(20));
        testOrder.setPlatformBonus(BigDecimal.valueOf(0));
    }

    @org.junit.jupiter.api.AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private void authenticatedAs(UUID userId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        userId.toString(), "n/a", java.util.List.of()));
    }

    /**
     * The controller re-checks the policy in the method body as well as in @PreAuthorize, because
     * standalone MockMvc does not apply method security. That body check is what this exercises.
     */
    @Test
    void getOrderEarnings_ReturnsTheBreakdown_WhenTheDriverOwnsTheOrder() throws Exception {
        authenticatedAs(driverId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(moneyAccessPolicy.canAccessMoney(any(), eq(MoneyOwnerType.DRIVER), eq(driverId))).thenReturn(true);

        mockMvc.perform(get("/api/v1/money/driver/" + driverId + "/orders/" + orderId))
                .andExpect(status().isOk())
                // Rupees as the API stores them, not paise.
                .andExpect(jsonPath("$.grossPayout").value(25))
                .andExpect(jsonPath("$.taxes").value(10))
                .andExpect(jsonPath("$.netPayout").value(35));
    }

    /** Another rider's earnings are not this rider's business. */
    @Test
    void getOrderEarnings_IsForbidden_WhenThePolicyRefuses() throws Exception {
        authenticatedAs(UUID.randomUUID());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(moneyAccessPolicy.canAccessMoney(any(), eq(MoneyOwnerType.DRIVER), eq(driverId))).thenReturn(false);

        mockMvc.perform(get("/api/v1/money/driver/" + driverId + "/orders/" + orderId))
                .andExpect(status().isForbidden());
    }

    /** An order delivered by someone else must not be readable by asking for it under your own id. */
    @Test
    void getOrderEarnings_IsNotFound_WhenTheOrderBelongsToAnotherDriver() throws Exception {
        authenticatedAs(driverId);
        testOrder.setDeliveryExecutiveId(UUID.randomUUID());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        mockMvc.perform(get("/api/v1/money/driver/" + driverId + "/orders/" + orderId))
                .andExpect(status().isNotFound());

        // The ownership check must come first: the policy is never consulted for someone else's order.
        verify(moneyAccessPolicy, never()).canAccessMoney(any(), any(), any());
    }

    @Test
    void getOrderEarnings_IsNotFound_WhenTheOrderDoesNotExist() throws Exception {
        authenticatedAs(driverId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/money/driver/" + driverId + "/orders/" + orderId))
                .andExpect(status().isNotFound());
    }

    // ---- the rider's [from, to) window ----

    private org.springframework.security.core.Authentication rider() {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                driverId.toString(), "n/a", java.util.List.of());
    }

    /** New York's fall-back day, 25 hours, exactly as the rider's browser sent it. */
    @Test
    void summaryIsForTheRidersOwnWindow() throws Exception {
        TimeWindow fallBackDay = new TimeWindow(java.time.Instant.parse("2026-11-01T04:00:00Z"), java.time.Instant.parse("2026-11-02T05:00:00Z"));
        when(driverSummaryService.getSummary(driverId, fallBackDay)).thenReturn(new DriverSummary());

        mockMvc.perform(get("/api/v1/money/driver/summary").principal(rider())
                        .param("from", "2026-11-01T04:00:00Z").param("to", "2026-11-02T05:00:00Z"))
                .andExpect(status().isOk());

        verify(driverSummaryService).getSummary(driverId, fallBackDay);
    }

    /**
     * No window, half a window, a zone-less or unreadable instant, or one that ends before it starts
     * is a 400 before anything is summed. {@code period} is not a parameter any more: the server
     * never read it, and a rider has no zone to read one in.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(nullValues = "NONE", value = {
            "NONE, NONE",
            "2026-09-01T00:00:00Z, NONE",
            "NONE, 2026-10-01T00:00:00Z",
            "2026-09-01T00:00:00, 2026-10-01T00:00:00Z",
            "2026-09-01T00:00:00Z, 2026-10-01",
            "month, 2026-10-01T00:00:00Z",
            "2026-10-01T00:00:00Z, 2026-09-01T00:00:00Z",
            "2026-09-01T00:00:00Z, 2026-09-01T00:00:00Z"})
    void summaryWithoutAValidWindowIsA400(String from, String to) throws Exception {
        var request = get("/api/v1/money/driver/summary").principal(rider()).param("period", "month");
        if (from != null) request.param("from", from);
        if (to != null) request.param("to", to);

        mockMvc.perform(request).andExpect(status().isBadRequest());

        verifyNoInteractions(driverSummaryService);
    }
}
