package com.fooddelivery.order.controller;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.common.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final IOrderRepository orderRepository;

    @GetMapping("/driver/{driverId}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'DELIVERY')")
    public ResponseEntity<List<Order>> getActiveOrdersForDriver(@PathVariable UUID driverId) {
        log.debug("Fetching active orders for driver {}", driverId);
        List<OrderStatus> inactiveStatuses = Arrays.asList(
                OrderStatus.DELIVERED,
                OrderStatus.DELIVERY_FAILED,
                OrderStatus.CANCELLED,
                OrderStatus.CANCELLED_BY_RESTAURANT
        );
        List<Order> activeOrders = orderRepository.findByDeliveryExecutiveIdAndStatusNotIn(driverId, inactiveStatuses);
        return ResponseEntity.ok(activeOrders);
    }

    @GetMapping("/driver/{driverId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'DELIVERY')")
    public ResponseEntity<List<Order>> getOrderHistoryForDriver(
            @PathVariable UUID driverId,
            @RequestParam(required = false) String date) {
        
        log.debug("Fetching order history for driver {} for date {}", driverId, date);
        LocalDate queryDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime startOfDay = queryDate.atStartOfDay();
        LocalDateTime endOfDay = queryDate.atTime(LocalTime.MAX);

        List<Order> history = orderRepository.findByDeliveryExecutiveIdAndStatusAndCreatedAtBetween(
                driverId, OrderStatus.DELIVERED, startOfDay, endOfDay);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasAnyRole('ADMIN', 'DELIVERY')")
    public ResponseEntity<List<Order>> getUnassignedOrders() {
        log.debug("Fetching unassigned orders for broadcast");
        List<OrderStatus> dispatchableStatuses = Arrays.asList(
                OrderStatus.ACCEPTED,
                OrderStatus.PREPARING,
                OrderStatus.READY_FOR_PICKUP
        );
        List<Order> unassignedOrders = orderRepository.findByStatusInAndDeliveryExecutiveIdIsNull(dispatchableStatuses);
        return ResponseEntity.ok(unassignedOrders);
    }
}
