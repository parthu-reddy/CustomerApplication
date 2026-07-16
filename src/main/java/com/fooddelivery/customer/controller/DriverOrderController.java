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

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getDriverOrders(java.security.Principal principal) {
        UUID driverId = UUID.fromString(principal.getName());
        List<Order> orders = new ArrayList<>(orderRepository.findByDeliveryExecutiveId(driverId));
        
        // Find pings for this driver
        Set<String> keys = redisTemplate.keys("order:ping:pending:*");
        if (keys != null) {
            for (String key : keys) {
                String pingedDriver = redisTemplate.opsForValue().get(key);
                if (driverId.toString().equals(pingedDriver)) {
                    String orderIdStr = key.replace("order:ping:pending:", "");
                    try {
                        UUID orderId = UUID.fromString(orderIdStr);
                        orderRepository.findById(orderId).ifPresent(order -> {
                            if (!orders.contains(order)) {
                                orders.add(order);
                            }
                        });
                    } catch (Exception e) {
                        // ignore invalid uuid
                    }
                }
            }
        }
        
        List<OrderResponse> responses = orders.stream().map(this::mapToResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(responses, "Driver orders retrieved"));
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .menuItemId(item.getMenuItemId())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .restaurantId(order.getRestaurantId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .riderId(order.getDeliveryExecutiveId())
                .build();
    }
}
