package com.fooddelivery.order.controller;

import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/admin/orders/intervention")
public class AdminOrderManualController {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminOrderManualController.class);
    private final IOrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @GetMapping
    @PreAuthorize("hasRole(\'ADMIN\')")
    public ResponseEntity<Page<Order>> getOrdersRequiringIntervention(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        log.info("Fetching orders requiring manual intervention. Page: {}, Size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderRepository.findByDeliveryStatusOrderByCreatedAtDesc(com.fooddelivery.common.enums.DeliveryStatus.MANUAL_INTERVENTION_REQUIRED, pageable);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/{orderId}/assign-driver")
    @PreAuthorize("hasRole(\'ADMIN\')")
    public ResponseEntity<ApiResponse<String>> assignDriver(@PathVariable UUID orderId, @RequestBody Map<String, String> payload) {
        String driverIdStr = payload.get("deliveryExecutiveId");
        if (driverIdStr == null || driverIdStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("deliveryExecutiveId is required"));
        }
        try {
            UUID driverId = UUID.fromString(driverIdStr);
            // Check current status
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Order order = orderOpt.get();
            if (order.getDeliveryStatus() != com.fooddelivery.common.enums.DeliveryStatus.MANUAL_INTERVENTION_REQUIRED) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Order is no longer in MANUAL_INTERVENTION_REQUIRED delivery status. Current delivery status: " + order.getDeliveryStatus()));
            }
            // Publish DELIVERY_EXECUTIVE_ASSIGNED event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("orderId", order.getId().toString());
            eventPayload.put("customerId", order.getCustomerId().toString());
            eventPayload.put("driverId", driverId.toString());
            eventPayload.put("timestamp", System.currentTimeMillis());
            Map<String, Object> kafkaMessage = new HashMap<>();
            kafkaMessage.put("eventType", EventType.FORCE_ASSIGN_DRIVER.name());
            kafkaMessage.put("payload", eventPayload);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonMessage = mapper.writeValueAsString(kafkaMessage);
            kafkaTemplate.send(KafkaConstants.TOPIC_ORDER_EVENTS, jsonMessage);
            log.info("Admin successfully requested manual assignment of driver {} to order {}", driverId, order.getId());
            return ResponseEntity.ok(ApiResponse.success("Driver assignment requested successfully", "Operation successful"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid deliveryExecutiveId format"));
        } catch (Exception e) {
            log.error("Error during manual driver assignment", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to assign driver"));
        }
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole(\'ADMIN\')")
    public ResponseEntity<ApiResponse<String>> cancelOrder(@PathVariable UUID orderId, @RequestBody Map<String, String> payload) {
        String reason = payload.getOrDefault("reason", "Cancelled by Admin due to dispatch failure");
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Order order = orderOpt.get();
            if (order.getDeliveryStatus() != com.fooddelivery.common.enums.DeliveryStatus.MANUAL_INTERVENTION_REQUIRED) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Order is no longer in MANUAL_INTERVENTION_REQUIRED delivery status. Current delivery status: " + order.getDeliveryStatus()));
            }
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("orderId", order.getId().toString());
            eventPayload.put("customerId", order.getCustomerId().toString());
            eventPayload.put("reason", reason);
            eventPayload.put("timestamp", System.currentTimeMillis());
            // Use ORDER_CANCELLED_BY_ADMIN or standard cancellation
            Map<String, Object> kafkaMessage = new HashMap<>();
            kafkaMessage.put("eventType", EventType.ORDER_CANCELLED_BY_ADMIN.name());
            kafkaMessage.put("payload", eventPayload);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonMessage = mapper.writeValueAsString(kafkaMessage);
            kafkaTemplate.send(KafkaConstants.TOPIC_ORDER_EVENTS, jsonMessage);
            log.info("Admin successfully requested manual cancellation for order {}. Reason: {}", order.getId(), reason);
            return ResponseEntity.ok(ApiResponse.success("Order cancellation requested successfully", "Operation successful"));
        } catch (Exception e) {
            log.error("Error during manual order cancellation", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to cancel order"));
        }
    }

    @java.lang.SuppressWarnings("all")
    public AdminOrderManualController(final IOrderRepository orderRepository, final KafkaTemplate<String, String> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }
}
