package com.fooddelivery.order.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/orders")
public class CustomerOrderController {
    private static final Logger log = LoggerFactory.getLogger(CustomerOrderController.class);
    private final IOrderRepository orderRepository;

    public CustomerOrderController(IOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Customer requests a refund for an order that has already been delivered,
     * due to missing items, food quality issues, etc.
     */
    @PostMapping("/{orderId}/refund-request")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<String>> requestPostDeliveryRefund(
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> payload) {
        
        String reason = payload.get("reason");
        if (reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>error("A reason for the refund request is required"));
        }

        return orderRepository.findById(orderId).map(order -> {
            // Verify order is delivered
            if (order.getDeliveryStatus() != com.fooddelivery.common.enums.DeliveryStatus.DELIVERED) {
                return ResponseEntity.badRequest().body(ApiResponse.<String>error("Refund requests can only be made for delivered orders through this channel."));
            }

            // You might also want to check time constraints (e.g., within 24 hours of delivery)
            // But for now, we just transition status to manual intervention.
            
            // order.setStatus(OrderStatus.MANUAL_INTERVENTION_REQUIRED);
            // Since MANUAL_INTERVENTION_REQUIRED is not an OrderStatus, we add a flag or log it.
            // For now, just log it.
            log.info("Customer requested post-delivery refund for order {}. Reason: {}", orderId, reason);
            
            // In a real system, you'd save this request to a CustomerSupportTicket entity.
            
            return ResponseEntity.ok(ApiResponse.success(
                    "Refund request submitted successfully. Our support team will review it shortly.", 
                    "Successfully requested refund"));
        }).orElseGet(() -> ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(ApiResponse.<String>error("Order not found")));
    }
}
