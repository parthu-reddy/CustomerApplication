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
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<Order>> getActiveOrdersForDriver(@PathVariable UUID driverId) {
        log.debug("Fetching active orders for driver {}", driverId);
        List<OrderStatus> cancelledStatuses = Arrays.asList(
                OrderStatus.CANCELLED,
                OrderStatus.CANCELLED_BY_RESTAURANT
        );
        List<Order> activeOrders = orderRepository.findActiveOrdersForDriver(
                driverId, 
                cancelledStatuses, 
                java.util.List.of(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED, com.fooddelivery.common.enums.DeliveryStatus.FAILED, com.fooddelivery.common.enums.DeliveryStatus.CANCELLED));
        return ResponseEntity.ok(activeOrders);
    }

    @GetMapping("/driver/{driverId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'DELIVERY')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<Order>> getOrderHistoryForDriver(
            @PathVariable UUID driverId,
            @RequestParam(required = false) String date) {
        
        log.debug("Fetching order history for driver {} for date {}", driverId, date);
        LocalDate queryDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime startOfDay = queryDate.atStartOfDay();
        LocalDateTime endOfDay = queryDate.atTime(LocalTime.MAX);

        List<OrderStatus> cancelledStatuses = Arrays.asList(
                OrderStatus.CANCELLED,
                OrderStatus.CANCELLED_BY_RESTAURANT
        );
        List<Order> history = orderRepository.findHistoryOrdersForDriver(
                driverId, 
                cancelledStatuses, 
                java.util.List.of(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED, com.fooddelivery.common.enums.DeliveryStatus.FAILED, com.fooddelivery.common.enums.DeliveryStatus.CANCELLED), 
                startOfDay, 
                endOfDay);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasAnyRole('ADMIN', 'DELIVERY')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
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

    @GetMapping("/{orderId}/participants")
    public ResponseEntity<List<String>> getOrderParticipants(@PathVariable UUID orderId) {
        log.debug("Fetching authorized participants for order {}", orderId);
        return orderRepository.findById(orderId)
                .map(order -> {
                    List<String> participants = new java.util.ArrayList<>();
                    if (order.getCustomerId() != null) participants.add(order.getCustomerId().toString());
                    if (order.getRestaurantId() != null) participants.add(order.getRestaurantId().toString());
                    if (order.getDeliveryExecutiveId() != null) participants.add(order.getDeliveryExecutiveId().toString());
                    return ResponseEntity.ok(participants);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
