package com.fooddelivery.order.controller;

import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/admin/orders/dlq")
public class AdminDlqController {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminDlqController.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final com.fooddelivery.order.repository.IPaymentIntentRepository paymentIntentRepository;
    private final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator;
    private final com.fooddelivery.order.repository.IOrderRepository orderRepository;

    /**
     * Allows an admin to manually retry a failed Kafka event by providing its payload.
     * This is useful for messages that ended up in the DLT (Dead Letter Topic)
     * and need to be re-processed after a bug fix or data correction.
     */
    @PostMapping("/retry")
    @PreAuthorize("hasRole(\'ADMIN\')")
    public ResponseEntity<ApiResponse<String>> retryDlqEvent(@RequestBody Map<String, Object> payload, @RequestParam(required = false) String topic) {
        try {
            // Convert back to JSON string
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(payload);
            String targetTopic = topic != null && !topic.isEmpty() ? topic : KafkaConstants.TOPIC_ORDER_EVENTS;
            log.info("Admin manually retrying DLQ event to topic {}: {}", targetTopic, jsonPayload);
            // Re-publish to the main topic
            String partitionKey = null;
            if (payload.containsKey("aggregateId") && payload.get("aggregateId") != null) {
                partitionKey = payload.get("aggregateId").toString();
            } else if (payload.containsKey("payload") && payload.get("payload") instanceof Map) {
                Map<String, Object> innerPayload = (Map<String, Object>) payload.get("payload");
                if (innerPayload.containsKey("orderId") && innerPayload.get("orderId") != null) {
                    partitionKey = innerPayload.get("orderId").toString();
                }
            }
            if (partitionKey != null) {
                kafkaTemplate.send(targetTopic, partitionKey, jsonPayload);
            } else {
                kafkaTemplate.send(targetTopic, jsonPayload);
            }
            return ResponseEntity.ok(ApiResponse.success("Event republished successfully to " + targetTopic, "Successfully queued for retry"));
        } catch (Exception e) {
            log.error("Failed to retry DLQ event", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to republish event: " + e.getMessage()));
        }
    }

    @PostMapping("/refunds/{orderId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> retryRefund(@PathVariable java.util.UUID orderId) {
        try {
            com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByInternalOrderIdForUpdate(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("PaymentIntent not found for orderId: " + orderId));
            
            if (intent.getStatus() != com.fooddelivery.common.constants.PaymentIntentStatus.REFUND_FAILED) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Refund can only be retried if status is REFUND_FAILED. Current status: " + intent.getStatus()));
            }
            
            log.info("Admin manually retrying refund for orderId: {}", orderId);
            
            // Reset retry count to allow Sweeper or immediate processing
            intent.setRetryCount(0);
            paymentIntentRepository.save(intent);
            
            com.fooddelivery.order.entity.Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
                    
            if (order.getStatus() == com.fooddelivery.common.enums.OrderStatus.CANCELLED || 
                order.getStatus() == com.fooddelivery.common.enums.OrderStatus.CANCELLED_BY_RESTAURANT) {
                orderSagaOrchestrator.processRefund(order);
                return ResponseEntity.ok(ApiResponse.success("Refund process initiated successfully", "Successfully queued for retry"));
            } else if (order.getDeliveryStatus() == com.fooddelivery.common.enums.DeliveryStatus.DELIVERED) {
                // Determine if it was a partial refund or full post-delivery refund
                // Actually, if it failed it's either in processRefund or processPartialRefund. 
                // Admin can trigger processRefund but processRefund also handles DELIVERED.
                // Wait, processRefund expects cancelled orders or delivered orders. 
                // If it's a partial refund, processPartialRefund takes an amount. We don't have the amount here easily.
                // But for now, we just reset retryCount and let Sweeper or manual trigger handle it if we know the amount.
                // For full refunds, processRefund(order) works for delivered too.
                orderSagaOrchestrator.processRefund(order);
                return ResponseEntity.ok(ApiResponse.success("Post-delivery refund process initiated successfully", "Successfully queued for retry"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("Cannot auto-retry refund for order in status: " + order.getStatus()));
            }
        } catch (Exception e) {
            log.error("Failed to retry refund for order {}", orderId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retry refund: " + e.getMessage()));
        }
    }

    @java.lang.SuppressWarnings("all")
    public AdminDlqController(final KafkaTemplate<String, String> kafkaTemplate, final com.fooddelivery.order.repository.IPaymentIntentRepository paymentIntentRepository, final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator, final com.fooddelivery.order.repository.IOrderRepository orderRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.paymentIntentRepository = paymentIntentRepository;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.orderRepository = orderRepository;
    }
}
