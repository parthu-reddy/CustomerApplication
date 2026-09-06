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

    @GetMapping("/admin/refunds")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.List<com.fooddelivery.order.refund.RefundView> getRefunds() {
        return new java.util.ArrayList<>();
    }
}
