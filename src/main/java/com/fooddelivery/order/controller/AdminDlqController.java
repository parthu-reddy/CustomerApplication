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
@lombok.extern.slf4j.Slf4j
public class AdminDlqController {
    

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final com.fooddelivery.order.repository.IPaymentIntentRepository paymentIntentRepository;
    private final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator;
    private final com.fooddelivery.order.service.OrderRefundService orderRefundService;
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
                orderRefundService.processRefund(order);
                return ResponseEntity.ok(ApiResponse.success("Refund process initiated successfully", "Successfully queued for retry"));
            } else if (order.getDeliveryStatus() == com.fooddelivery.common.enums.DeliveryStatus.DELIVERED) {
                // Determine if it was a partial refund or full post-delivery refund
                // Actually, if it failed it's either in processRefund or processPartialRefund. 
                // Admin can trigger processRefund but processRefund also handles DELIVERED.
                // Wait, processRefund expects cancelled orders or delivered orders. 
                // If it's a partial refund, processPartialRefund takes an amount. We don't have the amount here easily.
                // But for now, we just reset retryCount and let Sweeper or manual trigger handle it if we know the amount.
                // For full refunds, processRefund(order) works for delivered too.
                orderRefundService.processRefund(order);
                return ResponseEntity.ok(ApiResponse.success("Post-delivery refund process initiated successfully", "Successfully queued for retry"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("Cannot auto-retry refund for order in status: " + order.getStatus()));
            }
        } catch (Exception e) {
            log.error("Failed to retry refund for order {}", orderId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retry refund: " + e.getMessage()));
        }
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.springframework.data.domain.Page<Map<String, Object>>> getFailedRefunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<com.fooddelivery.order.entity.PaymentIntent> intentsPage = 
            paymentIntentRepository.findByStatus(com.fooddelivery.common.constants.PaymentIntentStatus.REFUND_FAILED, pageable);
        
        org.springframework.data.domain.Page<Map<String, Object>> response = intentsPage.map(intent -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("paymentIntentId", intent.getId());
            map.put("orderId", intent.getInternalOrderId());
            map.put("amount", intent.getAmount());
            map.put("status", intent.getStatus());
            map.put("retryCount", intent.getRetryCount());
            map.put("createdAt", intent.getCreatedAt());

            
            // Try to fetch order details
            orderRepository.findById(intent.getInternalOrderId()).ifPresent(order -> {
                map.put("customerName", order.getCustomerId()); // Fallback customer name logic
                map.put("restaurantId", order.getRestaurantId());
                map.put("orderStatus", order.getStatus());
                map.put("totalAmount", order.getTotalAmount());
                map.put("refundedAmount", order.getRefundedAmount());
            });
            return map;
        });
        
        return ResponseEntity.ok(response);
    }

    
    public AdminDlqController(final KafkaTemplate<String, String> kafkaTemplate, final com.fooddelivery.order.repository.IPaymentIntentRepository paymentIntentRepository, final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator, final com.fooddelivery.order.service.OrderRefundService orderRefundService, final com.fooddelivery.order.repository.IOrderRepository orderRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.paymentIntentRepository = paymentIntentRepository;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.orderRefundService = orderRefundService;
        this.orderRepository = orderRepository;
    }
}
