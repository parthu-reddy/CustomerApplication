package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.dto.OrderResponse;
import com.fooddelivery.customer.service.CustomerOrderService;
import com.fooddelivery.order.entity.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/orders")
public class InternalOrderController {
    
    private final OrderController orderController;
    private final CustomerOrderService customerOrderService;

    public InternalOrderController(OrderController orderController, CustomerOrderService customerOrderService) {
        this.orderController = orderController;
        this.customerOrderService = customerOrderService;
    }

    @GetMapping("/{orderId}/invoice")
    public ResponseEntity<OrderResponse> getOrderInvoice(@PathVariable UUID orderId) {
        Order order = customerOrderService.getOrderById(orderId);
        OrderResponse response = orderController.mapToResponse(order);
        return ResponseEntity.ok(response);
    }
}
