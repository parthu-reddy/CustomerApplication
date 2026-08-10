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
    private final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator;

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
            kafkaTemplate.send(KafkaConstants.TOPIC_ORDER_EVENTS, order.getId().toString(), jsonMessage);
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
            kafkaTemplate.send(KafkaConstants.TOPIC_ORDER_EVENTS, order.getId().toString(), jsonMessage);
            log.info("Admin successfully requested manual cancellation for order {}. Reason: {}", order.getId(), reason);
            return ResponseEntity.ok(ApiResponse.success("Order cancellation requested successfully", "Operation successful"));
        } catch (Exception e) {
            log.error("Error during manual order cancellation", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to cancel order"));
        }
    }

    @PostMapping("/{orderId}/force-cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> forceCancelOrder(@PathVariable UUID orderId, @RequestBody Map<String, String> payload) {
        String reason = payload.getOrDefault("reason", "Force Cancelled by Admin");
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Order order = orderOpt.get();
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancellationReason(reason);
            orderRepository.save(order);
            orderSagaOrchestrator.processRefund(order);
            log.info("Admin forcefully cancelled order {}. Reason: {}", order.getId(), reason);
            return ResponseEntity.ok(ApiResponse.success("Order forcefully cancelled and refund requested", "Operation successful"));
        } catch (Exception e) {
            log.error("Error during force cancel", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to force cancel order"));
        }
    }

    @PostMapping("/{orderId}/force-refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> forceRefund(@PathVariable UUID orderId) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Order order = orderOpt.get();
            orderSagaOrchestrator.processRefund(order);
            log.info("Admin forcefully requested refund for order {}", order.getId());
            return ResponseEntity.ok(ApiResponse.success("Force refund requested successfully", "Operation successful"));
        } catch (Exception e) {
            log.error("Error during force refund", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to force refund"));
        }
    }
    @PostMapping("/{orderId}/refund/partial")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> partialRefund(@PathVariable UUID orderId, @RequestBody Map<String, Object> payload) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Order order = orderOpt.get();
            Object amountObj = payload.get("amount");
            if (amountObj == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Amount is required"));
            }
            java.math.BigDecimal amount = new java.math.BigDecimal(amountObj.toString());
            orderSagaOrchestrator.processPartialRefund(order, amount);
            log.info("Admin requested partial refund of {} for order {}", amount, order.getId());
            return ResponseEntity.ok(ApiResponse.success("Partial refund requested successfully", "Operation successful"));
        } catch (Exception e) {
            log.error("Error during partial refund", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage() != null ? e.getMessage() : "Failed to process partial refund"));
        }
    }

    @PostMapping("/{orderId}/refund/post-delivery")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> postDeliveryRefund(@PathVariable UUID orderId, @RequestBody Map<String, Object> payload) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Order order = orderOpt.get();
            Object amountObj = payload.get("amount");
            if (amountObj == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Amount is required"));
            }
            java.math.BigDecimal amount = new java.math.BigDecimal(amountObj.toString());
            // Since the system defaults to Option A (Platform absorbs full loss), we just use GATEWAY destination.
            orderSagaOrchestrator.processPartialRefund(order, amount, com.fooddelivery.common.enums.RefundDestination.GATEWAY);
            log.info("Admin requested post-delivery refund of {} for order {}", amount, order.getId());
            return ResponseEntity.ok(ApiResponse.success("Post-delivery refund requested successfully", "Operation successful"));
        } catch (Exception e) {
            log.error("Error during post-delivery refund", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage() != null ? e.getMessage() : "Failed to process post-delivery refund"));
        }
    }


    @java.lang.SuppressWarnings("all")
    public AdminOrderManualController(final IOrderRepository orderRepository, final KafkaTemplate<String, String> kafkaTemplate, final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
    }
}
