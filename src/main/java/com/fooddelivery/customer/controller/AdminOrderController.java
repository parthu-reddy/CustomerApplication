package com.fooddelivery.customer.controller;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.common.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final IOrderRepository orderRepository;

    @GetMapping("/unassigned")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Order>> getUnassignedOrders() {
        List<Order> unassignedOrders = orderRepository.findByStatusInAndDeliveryExecutiveIdIsNull(
            List.of(OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP)
        );
        return ResponseEntity.ok(unassignedOrders);
    }
}
