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

    @GetMapping("/user/{userId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Order>> getActiveOrdersForUser(@org.springframework.web.bind.annotation.PathVariable java.util.UUID userId) {
        List<Order> activeOrders = orderRepository.findByCustomerId(userId).stream()
                .filter(order -> List.of(OrderStatus.CREATED, OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY).contains(order.getStatus()))
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(activeOrders);
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Order>> getUnassignedOrders() {
        List<Order> unassignedOrders = orderRepository.findByStatusInAndDeliveryExecutiveIdIsNull(
            List.of(OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP)
        );
        return ResponseEntity.ok(unassignedOrders);
    }

    @GetMapping("/active-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Order>> getAllActiveOrders() {
        List<Order> activeOrders = orderRepository.findByStatusIn(
            List.of(OrderStatus.CREATED, OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY)
        );
        // Sort by created at descending
        activeOrders.sort((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
        return ResponseEntity.ok(activeOrders);
    }
}
