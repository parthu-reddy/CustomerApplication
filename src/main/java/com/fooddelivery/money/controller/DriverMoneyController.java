package com.fooddelivery.money.controller;

import com.fooddelivery.common.dto.order.DriverOrderEarnings;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.common.security.money.MoneyAccessPolicy;
import com.fooddelivery.common.security.money.MoneyOwnerType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/money/driver")
@lombok.RequiredArgsConstructor



public class DriverMoneyController {

    private final IOrderRepository orderRepository;
    private final MoneyAccessPolicy moneyAccessPolicy;
    private final com.fooddelivery.customer.service.money.DriverSummaryService driverSummaryService;
    private final com.fooddelivery.customer.client.LedgerClient ledgerClient;

    /**
     * Owner-scoped endpoint: drivers access their own order earnings.
     */
    @GetMapping("/{driverId}/orders/{orderId}")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).DRIVER, #driverId)")
    public ResponseEntity<DriverOrderEarnings> fetchOrderEarnings(
            @PathVariable("driverId") UUID driverId,
            @PathVariable("orderId") UUID orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getDeliveryExecutiveId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order has no assigned driver");
        }
        if (!order.getDeliveryExecutiveId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order does not belong to this driver");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!moneyAccessPolicy.canAccessMoney(authentication, MoneyOwnerType.DRIVER, driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: You cannot view this order's earnings");
        }

        return ResponseEntity.ok(buildDriverEarnings(order));
    }

    private DriverOrderEarnings buildDriverEarnings(Order order) {
        DriverOrderEarnings earnings = new DriverOrderEarnings();
        earnings.setOrderId(order.getId());
        earnings.setDriverId(order.getDeliveryExecutiveId());

        earnings.setGrossPayout(requireFinancial(order.getDriverGrossPayout(), "driverGrossPayout"));
        earnings.setTaxes(requireFinancial(order.getDriverTaxes(), "driverTaxes"));
        earnings.setNetPayout(requireFinancial(order.getDriverNetPayout(), "driverNetPayout"));
        earnings.setCustomerContribution(requireFinancial(order.getDeliveryFee(), "deliveryFee"));
        earnings.setRestaurantContribution(requireFinancial(order.getRestaurantDeliveryContribution(), "restaurantDeliveryContribution"));
        earnings.setPlatformBonus(requireFinancial(order.getPlatformBonus(), "platformBonus"));

        return earnings;
    }

    private java.math.BigDecimal requireFinancial(java.math.BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalStateException("Missing financial value for: " + field);
        }
        return value;
    }

    /**
     * The rider's earnings for {@code [from, to)}: a period on the rider's own calendar (this month,
     * the last 30 days), computed in the rider's browser from the rider's zone. A rider has no zone on
     * record, so the server has no calendar to compute one on. This took a {@code period} it never
     * read and always answered with one UTC month. TimezoneCorrectness_2026-09-25.
     */
    @GetMapping("/summary")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).DRIVER, T(java.util.UUID).fromString(authentication.name))")
    public ResponseEntity<com.fooddelivery.customer.dto.DriverSummary> fetchSummary(
            @RequestParam("from") java.time.Instant from,
            @RequestParam("to") java.time.Instant to,
            Authentication authentication) {

        UUID driverId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(driverSummaryService.getSummary(driverId, new com.fooddelivery.common.time.TimeWindow(from, to)));
    }

    @GetMapping("/statement")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).DRIVER, T(java.util.UUID).fromString(authentication.name))")
    public ResponseEntity<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.common.dto.ledger.LedgerStatementLineDto>> fetchStatement(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Authentication authentication) {
        
        UUID driverId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ledgerClient.getStatement("DRIVER_PAYABLE", driverId, page, size));
    }

}
