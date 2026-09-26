package com.fooddelivery.customer.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.common.dto.order.DriverOrderEarnings;
import com.fooddelivery.common.dto.order.RestaurantOrderEarnings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/money")
public class InternalMoneyController {

    private final com.fooddelivery.order.repository.IOrderRepository orderRepository;

    public InternalMoneyController(com.fooddelivery.order.repository.IOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // Endpoint: /api/v1/internal/money/daily-totals
    @GetMapping("/daily-totals")
    @PreAuthorize("hasRole('SERVICE')")
    public com.fooddelivery.customer.dto.DailyTotalDto getDailyPaidOrderTotal(@RequestParam("from") java.time.Instant from, @RequestParam("to") java.time.Instant to) {
        // [from, to) is the ledger's accounting day (LedgerService.AccountingCalendar); no date is interpreted here.
        java.util.List<com.fooddelivery.common.enums.OrderStatus> excludedStatuses = java.util.List.of(
            com.fooddelivery.common.enums.OrderStatus.CANCELLED,
            com.fooddelivery.common.enums.OrderStatus.CANCELLED_BY_RESTAURANT
        );
        BigDecimal orderTotals = orderRepository.sumOrderTotalsInWindow(from, to, excludedStatuses);
        return new com.fooddelivery.customer.dto.DailyTotalDto(orderTotals);
    }

    // Endpoint: /api/v1/internal/money/daily-payables
    @GetMapping("/daily-payables")
    @PreAuthorize("hasRole('SERVICE')")
    public com.fooddelivery.customer.dto.DailyPayableDto getDailyPayables(@RequestParam("from") java.time.Instant from, @RequestParam("to") java.time.Instant to) {
        return new com.fooddelivery.customer.dto.DailyPayableDto(
                orderRepository.sumRestaurantPayableDeliveredInWindow(from, to),
                orderRepository.sumDriverPayableDeliveredInWindow(from, to));
    }

    // --- Driver Earnings Internal Endpoints ---

    @GetMapping("/driver/orders/{orderId}/earnings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<DriverOrderEarnings> fetchDriverOrderEarningsInternal(
            @PathVariable UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getDeliveryExecutiveId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order has no assigned driver");
        }
        return ResponseEntity.ok(buildDriverEarnings(order));
    }

    @PostMapping("/driver/{driverId}/orders:batch")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<java.util.List<DriverOrderEarnings>> fetchDriverOrderMoneyBatch(
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

    // --- Restaurant Earnings Internal Endpoints ---

    @GetMapping("/restaurant/orders/{orderId}/earnings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<RestaurantOrderEarnings> fetchRestaurantOrderEarningsInternal(
            @PathVariable UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return ResponseEntity.ok(buildRestaurantEarnings(order));
    }

    private RestaurantOrderEarnings buildRestaurantEarnings(Order order) {
        RestaurantOrderEarnings earnings = new RestaurantOrderEarnings();
        earnings.setOrderId(order.getId());
        earnings.setRestaurantId(order.getRestaurantId());
        java.math.BigDecimal itemTotal = order.getOrderItems().stream()
            .map(i -> i.getPrice().multiply(java.math.BigDecimal.valueOf(i.getQuantity())))
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        earnings.setFoodCost(requireFinancial(itemTotal, "itemTotal"));
        earnings.setPlatformFee(requireFinancial(order.getRestaurantPlatformFee(), "restaurantPlatformFee"));
        earnings.setDeliveryContribution(requireFinancial(order.getRestaurantDeliveryContribution(), "restaurantDeliveryContribution"));
        earnings.setNetPayout(requireFinancial(order.getRestaurantPayout(), "restaurantPayout"));
        return earnings;
    }

    private java.math.BigDecimal requireFinancial(java.math.BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalStateException("Missing financial value for: " + field);
        }
        return value;
    }


    // Deleted 2026-09-09: GET /admin/refunds returned `new ArrayList<>()` unconditionally -- an
    // ADMIN endpoint that answered "no refunds exist" whatever the database held. Nothing called it
    // (the generated UI client carried it, but no component used it), and there is no admin
    // refund-listing screen: the admin refund queue works from support tickets via
    // AdminRefundController, and stuck refunds appear under Money Operations. An endpoint that
    // lies is worse than one that is absent; if a refund list is wanted, it should be built
    // against a real screen.
}
