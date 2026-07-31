package com.fooddelivery.customer.controller;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.common.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import com.fooddelivery.customer.client.RestaurantClient;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.customer.dto.PartialRefundRequest;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/internal/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final IOrderRepository orderRepository;
    private final RestaurantClient restaurantClient;
    private final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator;
    
    @GetMapping("/user/{userId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Order>> getActiveOrdersForUser(@org.springframework.web.bind.annotation.PathVariable java.util.UUID userId) {
        List<Order> activeOrders = orderRepository.findByCustomerId(userId).stream()
                .filter(order -> List.of(OrderStatus.CREATED, OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP, OrderStatus.HANDED_OVER).contains(order.getStatus()))
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(activeOrders);
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Order>> getUnassignedOrders() {
        List<Order> unassignedOrders = orderRepository.findByStatusInAndDeliveryExecutiveIdIsNull(
            List.of(OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP)
        );
        return ResponseEntity.ok(unassignedOrders);
    }

    @GetMapping("/active-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Order>> getAllActiveOrders() {
        List<Order> activeOrders = orderRepository.findByStatusIn(
            List.of(OrderStatus.CREATED, OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP, OrderStatus.HANDED_OVER)
        );
        // Sort by created at descending
        activeOrders.sort((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
        return ResponseEntity.ok(activeOrders);
    }

    @PostMapping("/{orderId}/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> reconcileOrderState(@PathVariable UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        OrderStatus currentStatus = order.getStatus();
        OrderStatus highestStatus = currentStatus;
        String highestStatusSource = "CustomerApplication";

        // Check Restaurant Application
        try {
            Map<String, String> responseBody = restaurantClient.getInternalOrderStatus(orderId);
            
            if (responseBody != null) {
                String restaurantStatusStr = responseBody.get("status");
                // Need to translate restaurant status to customer status if there are any mismatches,
                // But for now let's assume they map directly or we catch IllegalArgumentException
                try {
                    OrderStatus restaurantStatus = OrderStatus.valueOf(restaurantStatusStr);
                    if (restaurantStatus.getSequence() > highestStatus.getSequence()) {
                        highestStatus = restaurantStatus;
                        highestStatusSource = "RestaurantApplication";
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore mapping issues for unknown statuses
                }
            }
        } catch (Exception e) {
            // Log and ignore
        }



        if (highestStatus.getSequence() > currentStatus.getSequence()) {
            // Fast-forward
            order.setStatus(highestStatus);
            orderRepository.save(order);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Order state successfully reconciled and fast-forwarded.",
                    "oldStatus", currentStatus.name(),
                    "newStatus", highestStatus.name(),
                    "source", highestStatusSource
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Order state is already up-to-date.",
                "currentStatus", currentStatus.name()
        ));
    }

    @PostMapping("/{orderId}/refund/partial")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> initiatePartialRefund(@PathVariable UUID orderId, @RequestBody PartialRefundRequest request) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (request.getAmount() == null || request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid refund amount"));
        }
        
        java.math.BigDecimal alreadyRefunded = order.getRefundedAmount() != null ? order.getRefundedAmount() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal remainingAmount = order.getTotalAmount().subtract(alreadyRefunded);
        
        if (request.getAmount().compareTo(remainingAmount) > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refund amount exceeds the refundable balance of the order."));
        }
        
        order.setRefundedAmount(alreadyRefunded.add(request.getAmount()));
        orderRepository.save(order);
        
        orderSagaOrchestrator.processPartialRefund(order, request.getAmount());
        
        return ResponseEntity.ok(Map.of("message", "Partial refund initiated successfully"));
    }
}
