package com.fooddelivery.customer.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public com.fooddelivery.customer.dto.DailyTotalDto getDailyPaidOrderTotal(@RequestParam("date") LocalDate date) {
        java.util.List<com.fooddelivery.common.enums.OrderStatus> excludedStatuses = java.util.List.of(
            com.fooddelivery.common.enums.OrderStatus.CANCELLED,
            com.fooddelivery.common.enums.OrderStatus.CANCELLED_BY_RESTAURANT
        );
        BigDecimal orderTotals = orderRepository.sumOrderTotalsByDate(date, excludedStatuses);
        return new com.fooddelivery.customer.dto.DailyTotalDto(orderTotals);
    }

    // Endpoint: /api/v1/internal/money/daily-payables
    @GetMapping("/daily-payables")
    @PreAuthorize("hasRole('SERVICE')")
    public com.fooddelivery.customer.dto.DailyPayableDto getDailyPayables(@RequestParam("date") LocalDate date) {
        return new com.fooddelivery.customer.dto.DailyPayableDto(
                orderRepository.sumRestaurantPayableByDeliveryDate(date),
                orderRepository.sumDriverPayableByDeliveryDate(date));
    }

    // Deleted 2026-09-09: GET /admin/refunds returned `new ArrayList<>()` unconditionally -- an
    // ADMIN endpoint that answered "no refunds exist" whatever the database held. Nothing called it
    // (the generated UI client carried it, but no component used it), and there is no admin
    // refund-listing screen: the admin refund queue works from support tickets via
    // AdminRefundController, and stuck refunds appear under Money Operations. An endpoint that
    // lies is worse than one that is absent; if a refund list is wanted, it should be built
    // against a real screen.
}
