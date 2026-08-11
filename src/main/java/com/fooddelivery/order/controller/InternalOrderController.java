package com.fooddelivery.order.controller;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.common.enums.OrderStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;

@RestController("orderInternalController")
@RequestMapping("/api/v1/internal/orders")
public class InternalOrderController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InternalOrderController.class);
    private final IOrderRepository orderRepository;
    private final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator;
    private final com.fooddelivery.order.service.OrderRefundService orderRefundService;

    @GetMapping("/driver/{driverId}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'DELIVERY')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<org.springframework.data.domain.Page<Order>> getActiveOrdersForDriver(
            @PathVariable UUID driverId,
            @org.springframework.data.web.PageableDefault(size = 50) org.springframework.data.domain.Pageable pageable) {
        log.debug("Fetching active orders for driver {}", driverId);
        List<OrderStatus> cancelledStatuses = Arrays.asList(OrderStatus.CANCELLED, OrderStatus.CANCELLED_BY_RESTAURANT);
        org.springframework.data.domain.Page<Order> activeOrders = orderRepository.findActiveOrdersForDriver(driverId, cancelledStatuses, java.util.List.of(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED, com.fooddelivery.common.enums.DeliveryStatus.FAILED, com.fooddelivery.common.enums.DeliveryStatus.CANCELLED), pageable);
        return ResponseEntity.ok(activeOrders);
    }

    @GetMapping("/driver/{driverId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'DELIVERY')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<org.springframework.data.domain.Page<Order>> getOrderHistoryForDriver(
            @PathVariable UUID driverId, 
            @RequestParam(required = false) String date,
            @org.springframework.data.web.PageableDefault(size = 50) org.springframework.data.domain.Pageable pageable) {
        log.debug("Fetching order history for driver {} for date {}", driverId, date);
        LocalDate queryDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime startOfDay = queryDate.atStartOfDay();
        LocalDateTime endOfDay = queryDate.atTime(LocalTime.MAX);
        List<OrderStatus> cancelledStatuses = Arrays.asList(OrderStatus.CANCELLED, OrderStatus.CANCELLED_BY_RESTAURANT);
        org.springframework.data.domain.Page<Order> history = orderRepository.findHistoryOrdersForDriver(driverId, cancelledStatuses, java.util.List.of(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED, com.fooddelivery.common.enums.DeliveryStatus.FAILED, com.fooddelivery.common.enums.DeliveryStatus.CANCELLED), startOfDay, endOfDay, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasAnyRole(\'ADMIN\', \'DELIVERY\')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<Order>> getUnassignedOrders() {
        log.debug("Fetching unassigned orders for broadcast");
        List<OrderStatus> dispatchableStatuses = Arrays.asList(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP);
        List<Order> unassignedOrders = orderRepository.findByStatusInAndDeliveryExecutiveIdIsNull(dispatchableStatuses, org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
        return ResponseEntity.ok(unassignedOrders);
    }

    @GetMapping("/{orderId}/participants")
    public ResponseEntity<List<String>> getOrderParticipants(@PathVariable UUID orderId) {
        log.debug("Fetching authorized participants for order {}", orderId);
        return orderRepository.findById(orderId).map(order -> {
            List<String> participants = new java.util.ArrayList<>();
            if (order.getCustomerId() != null) participants.add(order.getCustomerId().toString());
            if (order.getRestaurantId() != null) participants.add(order.getRestaurantId().toString());
            if (order.getDeliveryExecutiveId() != null) participants.add(order.getDeliveryExecutiveId().toString());
            return ResponseEntity.ok(participants);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{orderId}/partial-refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT')")
    public ResponseEntity<com.fooddelivery.common.dto.ApiResponse<String>> partialRefund(@PathVariable UUID orderId, @RequestBody java.util.Map<String, String> payload) {
        String amountStr = payload.get("amount");
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(com.fooddelivery.common.dto.ApiResponse.error("amount is required"));
        }
        try {
            java.math.BigDecimal amount = new java.math.BigDecimal(amountStr);
            return orderRepository.findById(orderId).map(order -> {
                orderRefundService.processPartialRefund(order, amount);
                log.info("Requested partial refund of {} for order {}", amount, orderId);
                return ResponseEntity.ok(com.fooddelivery.common.dto.ApiResponse.success("Partial refund requested successfully", "Operation successful"));
            }).orElse(ResponseEntity.notFound().build());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(com.fooddelivery.common.dto.ApiResponse.error("Invalid amount format"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(com.fooddelivery.common.dto.ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error during partial refund", e);
            return ResponseEntity.internalServerError().body(com.fooddelivery.common.dto.ApiResponse.error("Failed to request partial refund"));
        }
    }

    @java.lang.SuppressWarnings("all")
    public InternalOrderController(final IOrderRepository orderRepository, final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator, final com.fooddelivery.order.service.OrderRefundService orderRefundService) {
        this.orderRepository = orderRepository;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.orderRefundService = orderRefundService;
    }
}
