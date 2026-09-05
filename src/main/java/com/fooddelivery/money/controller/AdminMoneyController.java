package com.fooddelivery.money.controller;

import com.fooddelivery.customer.dto.AdminOrderMoney;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/admin")
@PreAuthorize("hasRole('ADMIN')")
@lombok.RequiredArgsConstructor
public class AdminMoneyController {

    private final com.fooddelivery.customer.service.AdminOrderMoneyService adminOrderMoneyService;

    /**
     * Admin-only full money view of an order, showing all parties' financials.
     * Full path: /api/v1/internal/admin/orders/{orderId}/money
     */
    @GetMapping("/orders/{orderId}/money")
    public ResponseEntity<AdminOrderMoney> getOrderMoney(@PathVariable UUID orderId) {
        return ResponseEntity.ok(adminOrderMoneyService.getOrderMoney(orderId));
    }
}
