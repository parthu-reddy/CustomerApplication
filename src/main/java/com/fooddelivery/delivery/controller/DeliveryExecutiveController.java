package com.fooddelivery.delivery.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryExecutiveController {

    private final DeliveryService deliveryService;

    @PostMapping("/onboard")
    public ResponseEntity<ApiResponse<com.fooddelivery.delivery.entity.DeliveryExecutive>> onboardDriver(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String phoneNumber = request.get("phoneNumber");
        String vehicleNumber = request.get("vehicleNumber");
        com.fooddelivery.delivery.entity.DeliveryExecutive executive = deliveryService.onboard(name, phoneNumber, vehicleNumber);
        return ResponseEntity.ok(ApiResponse.success(executive, "Delivery Executive onboarded successfully"));
    }

    @PostMapping("/status")
    public ResponseEntity<ApiResponse<Void>> toggleStatus(@RequestBody Map<String, Object> request) {
        UUID driverId = UUID.fromString((String) request.get("driverId"));
        boolean available = (Boolean) request.get("available");
        
        deliveryService.toggleStatus(driverId, available);
        
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Status updated successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/drivers/{driverId}/orders/{orderId}/accept")
    public ResponseEntity<ApiResponse<com.fooddelivery.order.entity.Order>> acceptOrder(
            @PathVariable UUID driverId, @PathVariable UUID orderId) {
        com.fooddelivery.order.entity.Order order = deliveryService.acceptOrderPing(driverId, orderId);
        return ResponseEntity.ok(ApiResponse.success(order, "Order accepted by driver"));
    }

    @PostMapping("/drivers/{driverId}/orders/{orderId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectOrder(
            @PathVariable UUID driverId, @PathVariable UUID orderId) {
        deliveryService.rejectOrderPing(driverId, orderId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Order rejected").build());
    }

    @PostMapping("/drivers/{driverId}/orders/{orderId}/status")
    public ResponseEntity<ApiResponse<com.fooddelivery.order.entity.Order>> updateOrderStatus(
            @PathVariable UUID driverId, @PathVariable UUID orderId, @RequestBody Map<String, String> request) {
        com.fooddelivery.order.enums.OrderStatus status = com.fooddelivery.order.enums.OrderStatus.valueOf(request.get("status"));
        com.fooddelivery.order.entity.Order order = deliveryService.updateOrderStatus(driverId, orderId, status);
        return ResponseEntity.ok(ApiResponse.success(order, "Order status updated"));
    }

    @PostMapping("/drivers/{driverId}/orders/{orderId}/timeout")
    public ResponseEntity<ApiResponse<Void>> timeoutDriver(
            @PathVariable UUID driverId, @PathVariable UUID orderId) {
        deliveryService.timeoutDriverPing(driverId, orderId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Driver ping timed out").build());
    }
}
