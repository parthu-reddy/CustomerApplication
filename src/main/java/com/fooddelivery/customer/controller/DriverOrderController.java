package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.dto.OrderResponse;
import com.fooddelivery.customer.dto.OrderItemResponse;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@PreAuthorize("hasRole('DELIVERY')")
public class DriverOrderController {

    private final IOrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/available")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAvailableOrders(java.security.Principal principal) {
        UUID driverId = UUID.fromString(principal.getName());
        List<OrderResponse> responses = new ArrayList<>();
        
        // Fast lookup for this driver's ping
        String orderIdStr = redisTemplate.opsForValue().get("driver:pending_ping:" + driverId.toString());
        if (orderIdStr != null) {
            try {
                UUID orderId = UUID.fromString(orderIdStr);
                orderRepository.findById(orderId).ifPresent(order -> {
                    if (order.getDeliveryExecutiveId() == null && 
                        (order.getStatus() == com.fooddelivery.common.enums.OrderStatus.ACCEPTED || 
                         order.getStatus() == com.fooddelivery.common.enums.OrderStatus.PREPARING || 
                         order.getStatus() == com.fooddelivery.common.enums.OrderStatus.READY_FOR_PICKUP)) {
                        
                        OrderResponse response = mapToResponse(order);
                        
                        // Set expiration time
                        Double score = redisTemplate.opsForZSet().score("order:ping:timeouts", orderIdStr);
                        if (score != null) {
                            response.setExpiresAt(score.longValue());
                        }
                        
                        responses.add(response);
                    }
                });
            } catch (Exception e) {
                // ignore invalid uuid
            }
        }
        
        return ResponseEntity.ok(ApiResponse.success(responses, "Available orders retrieved"));
    }

    @GetMapping("/active")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getActiveOrders(java.security.Principal principal) {
        UUID driverId = UUID.fromString(principal.getName());
        List<Order> activeOrders = orderRepository.findByDeliveryExecutiveId(driverId).stream()
                .filter(o -> o.getStatus() == com.fooddelivery.common.enums.OrderStatus.ACCEPTED || 
                             o.getStatus() == com.fooddelivery.common.enums.OrderStatus.PREPARING || 
                             o.getStatus() == com.fooddelivery.common.enums.OrderStatus.READY_FOR_PICKUP || 
                             o.getStatus() == com.fooddelivery.common.enums.OrderStatus.PICKED_UP)
                .collect(Collectors.toList());
        
        List<OrderResponse> responses = activeOrders.stream().map(this::mapToResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(responses, "Active orders retrieved"));
    }

    @GetMapping("/history")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getHistoryOrders(java.security.Principal principal, 
                                                                             @org.springframework.web.bind.annotation.RequestParam(required = false) String date) {
        UUID driverId = UUID.fromString(principal.getName());
        List<Order> terminalOrders = orderRepository.findByDeliveryExecutiveId(driverId).stream()
                .filter(o -> o.getStatus() == null || 
                             o.getStatus() == com.fooddelivery.common.enums.OrderStatus.DELIVERED || 
                             o.getStatus() == com.fooddelivery.common.enums.OrderStatus.CANCELLED ||
                             o.getStatus() == com.fooddelivery.common.enums.OrderStatus.CANCELLED_BY_RESTAURANT ||
                             o.getStatus() == com.fooddelivery.common.enums.OrderStatus.DELIVERY_FAILED ||
                             o.getStatus() == com.fooddelivery.common.enums.OrderStatus.PARTIALLY_REFUNDED ||
                             o.getStatus() == com.fooddelivery.common.enums.OrderStatus.CANCELLED_AND_REFUNDED)
                .filter(o -> {
                    if (date == null || date.isEmpty()) return true;
                    if (o.getCreatedAt() == null) return true;
                    return o.getCreatedAt().toLocalDate().toString().equals(date);
                })
                .collect(Collectors.toList());
        
        List<OrderResponse> responses = terminalOrders.stream().map(this::mapToResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(responses, "History orders retrieved"));
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = new java.util.ArrayList<>();
        if (order.getOrderItems() != null) {
            itemResponses = order.getOrderItems().stream()
                    .map(item -> OrderItemResponse.builder()
                            .id(item.getId())
                            .menuItemId(item.getMenuItemId())
                            .quantity(item.getQuantity())
                            .price(item.getPrice())
                            .build())
                    .collect(Collectors.toList());
        }

        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .restaurantId(order.getRestaurantId())
                .restaurantName(order.getRestaurantName())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryLat(order.getDeliveryLat())
                .deliveryLng(order.getDeliveryLng())

                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .riderId(order.getDeliveryExecutiveId())
                .build();
    }
}
