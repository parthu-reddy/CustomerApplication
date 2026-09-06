package com.fooddelivery.customer.controller;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.common.enums.OrderStatus;
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
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/internal/admin/orders")
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class AdminOrderController {
    

    private final IOrderRepository orderRepository;
    private final RestaurantClient restaurantClient;
    private final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator;
    private final com.fooddelivery.order.service.state.OrderActionService orderActionService;

    @GetMapping("/user/{userId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.fooddelivery.common.dto.ApiResponse<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.customer.dto.OrderResponse>>> getActiveOrdersForUser(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID userId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<Order> activeOrders = orderRepository.findByCustomerIdAndStatusInOrderByCreatedAtDesc(
            userId, 
            List.of(OrderStatus.CREATED, OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP, OrderStatus.HANDED_OVER), 
            pageable
        );
        return ResponseEntity.ok(com.fooddelivery.common.dto.ApiResponse.success(com.fooddelivery.common.dto.PageResponseDto.of(activeOrders.map(com.fooddelivery.customer.mapper.OrderMapper::mapToResponse)), "Active orders retrieved"));
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.fooddelivery.common.dto.ApiResponse<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.customer.dto.OrderResponse>>> getUnassignedOrders(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int size) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<Order> unassignedOrders = orderRepository.findByStatusInAndDeliveryExecutiveIdIsNullOrderByCreatedAtDesc(
            List.of(OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP), 
            pageable
        );
        return ResponseEntity.ok(com.fooddelivery.common.dto.ApiResponse.success(com.fooddelivery.common.dto.PageResponseDto.of(unassignedOrders.map(com.fooddelivery.customer.mapper.OrderMapper::mapToResponse)), "Unassigned orders retrieved"));
    }

    @GetMapping("/active-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.fooddelivery.common.dto.ApiResponse<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.customer.dto.OrderResponse>>> getAllActiveOrders(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int size) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
            page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        );
        org.springframework.data.domain.Page<Order> activeOrders = orderRepository.findByStatusIn(
            List.of(OrderStatus.CREATED, OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP, OrderStatus.HANDED_OVER),
            pageable
        );
        return ResponseEntity.ok(com.fooddelivery.common.dto.ApiResponse.success(com.fooddelivery.common.dto.PageResponseDto.of(activeOrders.map(com.fooddelivery.customer.mapper.OrderMapper::mapToResponse)), "All active orders retrieved"));
    }

    @PostMapping("/{orderId}/reconcile")
    @PreAuthorize("hasRole(\'ADMIN\')")
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
                    log.debug("Unknown restaurant status: {}", restaurantStatusStr);
                }
            }
        } catch (Exception e) {
            log.error("Failed to reconcile order state", e);
        }
        // Log and ignore
        if (highestStatus.getSequence() > currentStatus.getSequence()) {
            // Fast-forward
            order.setStatus(highestStatus);
            orderRepository.save(order);
            return ResponseEntity.ok(Map.of("message", "Order state successfully reconciled and fast-forwarded.", "oldStatus", currentStatus.name(), "newStatus", highestStatus.name(), "source", highestStatusSource));
        }
        return ResponseEntity.ok(Map.of("message", "Order state is already up-to-date.", "currentStatus", currentStatus.name()));
    }



    @PostMapping("/{orderId}/override-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> overrideOrderStatus(@PathVariable UUID orderId, @RequestBody Map<String, String> payload) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        
        String targetStatusStr = payload.get("targetStatus");
        if (targetStatusStr == null || targetStatusStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "targetStatus is required"));
        }

        try {
            OrderStatus newStatus = OrderStatus.valueOf(targetStatusStr);
            OrderStatus oldStatus = order.getStatus();
            order.setStatus(newStatus);
            orderRepository.save(order);
            
            log.warn("Admin forcefully overridden order {} status from {} to {}", orderId, oldStatus, newStatus);
            orderActionService.emitOrderStatusSyncEvent(order.getId(), newStatus);
            
            return ResponseEntity.ok(Map.of(
                "message", "Order status overridden successfully",
                "oldStatus", oldStatus.name(),
                "newStatus", newStatus.name()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid order status"));
        }
    }


    
}
