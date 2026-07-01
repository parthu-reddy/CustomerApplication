package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.dto.OrderRequest;
import com.fooddelivery.customer.dto.OrderResponse;
import com.fooddelivery.customer.dto.OrderItemResponse;
import com.fooddelivery.customer.service.CustomerOrderService;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CustomerOrderService customerOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@RequestBody OrderRequest request) {
        CustomerOrderService.OrderWithPayment result = customerOrderService.createOrderWithPayment(
                request.getCustomerId(),
                request.getRestaurantId(),
                request.getDeliveryAddressId(),
                request.getItems()
        );
        
        OrderResponse response = mapToResponse(result.order());
        response.setPaymentIntent(result.paymentIntent());
        
        return ResponseEntity.ok(ApiResponse.success(response, "Order created successfully"));
    }

    @PostMapping("/{orderId}/delay-approval")
    public ResponseEntity<ApiResponse<Void>> handleDelayApproval(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID orderId,
            @RequestBody com.fooddelivery.customer.dto.DelayApprovalRequest request) {
        
        customerOrderService.handleDelayApproval(orderId, request.isApproved());
        return ResponseEntity.ok(ApiResponse.success(null, "Delay approval processed"));
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
                .build();
    }
}
