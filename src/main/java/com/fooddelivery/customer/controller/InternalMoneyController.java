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
    public Map<String, BigDecimal> getDailyPaidOrderTotal(@RequestParam("date") LocalDate date) {
        java.util.List<com.fooddelivery.common.enums.OrderStatus> excludedStatuses = java.util.List.of(
            com.fooddelivery.common.enums.OrderStatus.CANCELLED,
            com.fooddelivery.common.enums.OrderStatus.CANCELLED_BY_RESTAURANT
        );
        BigDecimal orderTotals = orderRepository.sumOrderTotalsByDate(date, excludedStatuses);
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("orderTotals", orderTotals);
        return result;
    }

    @GetMapping("/admin/refunds")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getRefunds() {
        return new HashMap<>();
    }
}
