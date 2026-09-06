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
    public ResponseEntity<DriverOrderEarnings> getOrderEarnings(
            @PathVariable UUID driverId,
            @PathVariable UUID orderId) {

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

    /**
     * SERVICE-scoped twin: used by DeliveryExecutiveApplication via Feign.
     */
    @GetMapping("/orders/{orderId}/earnings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<DriverOrderEarnings> getOrderEarningsInternal(
            @PathVariable UUID orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getDeliveryExecutiveId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order has no assigned driver");
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

    @PostMapping("/{driverId}/orders:batch")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<java.util.List<DriverOrderEarnings>> getDriverOrderMoneyBatch(
            @PathVariable("driverId") UUID driverId,
            @RequestBody java.util.List<String> orderIds) {
        java.util.List<DriverOrderEarnings> batch = new java.util.ArrayList<>();
        for (String idStr : orderIds) {
            try {
                UUID orderId = UUID.fromString(idStr);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && driverId.equals(order.getDeliveryExecutiveId())) {
                    batch.add(buildDriverEarnings(order));
                }
            } catch (IllegalArgumentException e) {
                // ignore invalid format
            }
        }
        return ResponseEntity.ok(batch);
    }

    @GetMapping("/summary")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).DRIVER, T(java.util.UUID).fromString(authentication.name))")
    public ResponseEntity<com.fooddelivery.customer.dto.DriverSummary> getSummary(
            @RequestParam(required = false, defaultValue = "month") String period,
            Authentication authentication) {
        
        UUID driverId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(driverSummaryService.getSummary(driverId, period));
    }

    @GetMapping("/orders")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).DRIVER, T(java.util.UUID).fromString(authentication.name))")
    public ResponseEntity<java.util.List<DriverOrderEarnings>> getOrders(
            @RequestParam(required = false) String date,
            Authentication authentication) {
        
        UUID driverId = UUID.fromString(authentication.getName());
        
        java.time.LocalDateTime start = java.time.LocalDateTime.now().minusMonths(1);
        java.time.LocalDateTime end = java.time.LocalDateTime.now().plusDays(1);
        
        java.util.List<com.fooddelivery.common.enums.OrderStatus> cancelledStatuses = java.util.List.of(com.fooddelivery.common.enums.OrderStatus.CANCELLED);
        java.util.List<com.fooddelivery.common.enums.DeliveryStatus> terminalStatuses = java.util.List.of(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED);
        org.springframework.data.domain.Page<Order> ordersPage = orderRepository.findHistoryOrdersForDriver(
                driverId, cancelledStatuses, terminalStatuses, start, end, org.springframework.data.domain.PageRequest.of(0, 1000));
        
        java.util.List<DriverOrderEarnings> earnings = ordersPage.getContent().stream()
            .map(this::buildDriverEarnings)
            .collect(java.util.stream.Collectors.toList());
            
        return ResponseEntity.ok(earnings);
    }

    @GetMapping("/statement")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).DRIVER, T(java.util.UUID).fromString(authentication.name))")
    public ResponseEntity<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.common.dto.ledger.LedgerStatementLineDto>> getStatement(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Authentication authentication) {
        
        UUID driverId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ledgerClient.getStatement("DRIVER_PAYABLE", driverId, page, size));
    }

    @GetMapping("/cash")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).DRIVER, T(java.util.UUID).fromString(authentication.name))")
    public ResponseEntity<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.common.dto.ledger.CashRemittanceDto>> getCash(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Authentication authentication) {
        
        UUID driverId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ledgerClient.getCashByDriver(driverId, page, size));
    }
}
