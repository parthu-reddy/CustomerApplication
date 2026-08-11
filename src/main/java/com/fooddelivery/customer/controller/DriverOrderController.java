package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.dto.OrderResponse;
import com.fooddelivery.customer.dto.OrderItemResponse;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/delivery/orders")
@PreAuthorize("hasRole(\'DELIVERY\')")
public class DriverOrderController {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DriverOrderController.class);
    private final IOrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/available")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAvailableOrders(java.security.Principal principal) {
        UUID driverId = UUID.fromString(principal.getName());
        List<OrderResponse> responses = new ArrayList<>();
        // Fast lookup for this driver's ping
        String orderIdStr = redisTemplate.opsForValue().get(com.fooddelivery.common.constants.RedisKeyConstants.PREFIX_DRIVER_PENDING_PING + driverId.toString());
        if (orderIdStr != null) {
            try {
                UUID orderId = UUID.fromString(orderIdStr);
                orderRepository.findById(orderId).ifPresent(order -> {
                    if (order.getDeliveryExecutiveId() == null && (order.getStatus() == com.fooddelivery.common.enums.OrderStatus.ACCEPTED || order.getStatus() == com.fooddelivery.common.enums.OrderStatus.PREPARING || order.getStatus() == com.fooddelivery.common.enums.OrderStatus.READY_FOR_PICKUP)) {
                        OrderResponse response = mapToResponse(order);
                        // Set expiration time
                        Double score = redisTemplate.opsForZSet().score("order:ping:timeouts", orderIdStr);
                        if (score != null) {
                            response.setExpiresAt(score.longValue());
                            long remaining = Math.max(0, (score.longValue() - System.currentTimeMillis()) / 1000);
                            response.setRemainingPingSeconds(remaining);
                        }
                        responses.add(response);
                    }
                });
            } catch (Exception e) {
            }
        }
        // ignore invalid uuid
        return ResponseEntity.ok(ApiResponse.success(responses, "Available orders retrieved"));
    }

    @GetMapping("/active")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<OrderResponse>>> getActiveOrders(
            java.security.Principal principal, 
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        UUID driverId = UUID.fromString(principal.getName());
        
        List<com.fooddelivery.common.enums.OrderStatus> cancelledStatuses = List.of(
            com.fooddelivery.common.enums.OrderStatus.CANCELLED
        );
        List<com.fooddelivery.common.enums.DeliveryStatus> terminalStatuses = List.of(
            com.fooddelivery.common.enums.DeliveryStatus.DELIVERED,
            com.fooddelivery.common.enums.DeliveryStatus.FAILED,
            com.fooddelivery.common.enums.DeliveryStatus.CANCELLED
        );
        
        org.springframework.data.domain.Page<Order> activeOrders = orderRepository.findActiveOrdersForDriver(driverId, cancelledStatuses, terminalStatuses, pageable);
        org.springframework.data.domain.Page<OrderResponse> responses = activeOrders.map(this::mapToResponse);
        return ResponseEntity.ok(ApiResponse.success(responses, "Active orders retrieved"));
    }

    @GetMapping("/history")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<OrderResponse>>> getHistoryOrders(
            java.security.Principal principal, 
            @org.springframework.web.bind.annotation.RequestParam(required = false) String date,
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        UUID driverId = UUID.fromString(principal.getName());
        
        List<com.fooddelivery.common.enums.OrderStatus> cancelledStatuses = List.of(
            com.fooddelivery.common.enums.OrderStatus.CANCELLED
        );
        List<com.fooddelivery.common.enums.DeliveryStatus> terminalStatuses = List.of(
            com.fooddelivery.common.enums.DeliveryStatus.DELIVERED,
            com.fooddelivery.common.enums.DeliveryStatus.FAILED,
            com.fooddelivery.common.enums.DeliveryStatus.CANCELLED
        );

        java.time.LocalDateTime start = java.time.LocalDateTime.now().minusYears(10);
        java.time.LocalDateTime end = java.time.LocalDateTime.now().plusDays(1);
        if (date != null && !date.isEmpty()) {
            java.time.LocalDate parsedDate = java.time.LocalDate.parse(date);
            start = parsedDate.atStartOfDay();
            end = parsedDate.plusDays(1).atStartOfDay();
        }

        org.springframework.data.domain.Page<Order> terminalOrders = orderRepository.findHistoryOrdersForDriver(driverId, cancelledStatuses, terminalStatuses, start, end, pageable);
        org.springframework.data.domain.Page<OrderResponse> responses = terminalOrders.map(this::mapToResponse);
        return ResponseEntity.ok(ApiResponse.success(responses, "History orders retrieved"));
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = new java.util.ArrayList<>();
        if (order.getOrderItems() != null) {
            itemResponses = order.getOrderItems().stream().map(item -> OrderItemResponse.builder().id(item.getId()).menuItemId(item.getMenuItemId()).quantity(item.getQuantity()).price(item.getPrice()).build()).collect(Collectors.toList());
        }
        return OrderResponse.builder().id(order.getId()).customerId(order.getCustomerId()).restaurantId(order.getRestaurantId()).restaurantName(order.getRestaurantName()).status(order.getStatus()).deliveryStatus(order.getDeliveryStatus()).totalAmount(order.getTotalAmount()).deliveryAddress(order.getDeliveryAddress()).deliveryLat(order.getDeliveryLat()).deliveryLng(order.getDeliveryLng()).items(itemResponses).createdAt(order.getCreatedAt()).updatedAt(order.getUpdatedAt()).riderId(order.getDeliveryExecutiveId()).otp(order.getOtp()).pickupOtp(order.getPickupOtp()).estimatedCompletionTime(order.getEstimatedCompletionTime()).build();
    }

    @java.lang.SuppressWarnings("all")
    public DriverOrderController(final IOrderRepository orderRepository, final StringRedisTemplate redisTemplate) {
        this.orderRepository = orderRepository;
        this.redisTemplate = redisTemplate;
    }
}
