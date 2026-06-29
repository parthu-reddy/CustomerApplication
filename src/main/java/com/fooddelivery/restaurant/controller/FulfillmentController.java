package com.fooddelivery.restaurant.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.restaurant.service.FulfillmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/fulfillment")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<ApiResponse<Order>> acceptOrder(@PathVariable UUID restaurantId, @PathVariable UUID orderId) {
        Order order = fulfillmentService.acceptOrder(restaurantId, orderId);
        return ResponseEntity.ok(ApiResponse.success(order, "Order accepted and dispatched for logistics"));
    }

    @PostMapping("/orders/{orderId}/reject")
    public ResponseEntity<ApiResponse<Order>> rejectOrder(@PathVariable UUID restaurantId, @PathVariable UUID orderId) {
        Order order = fulfillmentService.rejectOrder(restaurantId, orderId);
        return ResponseEntity.ok(ApiResponse.success(order, "Order rejected by restaurant"));
    }

    @PostMapping("/orders/{orderId}/ready")
    public ResponseEntity<ApiResponse<Order>> readyOrder(@PathVariable UUID restaurantId, @PathVariable UUID orderId) {
        Order order = fulfillmentService.readyOrder(restaurantId, orderId);
        return ResponseEntity.ok(ApiResponse.success(order, "Order marked as ready for pickup"));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(@PathVariable UUID restaurantId, @PathVariable UUID orderId) {
        Order order = fulfillmentService.cancelOrderAfterAccept(restaurantId, orderId);
        return ResponseEntity.ok(ApiResponse.success(order, "Order cancelled by restaurant after acceptance"));
    }
}
