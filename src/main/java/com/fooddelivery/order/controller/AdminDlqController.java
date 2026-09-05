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
@lombok.RequiredArgsConstructor
public class AdminDlqController {
    

    private final com.fooddelivery.order.repository.IOrderRepository orderRepository;
    private final com.fooddelivery.order.repository.RefundRepository refundRepository;
    private final com.fooddelivery.order.refund.RefundService refundService;
    private final KafkaTemplate<String, String> kafkaTemplate;

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
            org.springframework.messaging.support.MessageBuilder<String> builder = org.springframework.messaging.support.MessageBuilder
                .withPayload(jsonPayload)
                .setHeader(org.springframework.kafka.support.KafkaHeaders.TOPIC, targetTopic);

            if (partitionKey != null) {
                builder.setHeader(org.springframework.kafka.support.KafkaHeaders.KEY, partitionKey);
            }
            
            if (payload.containsKey("eventId") && payload.get("eventId") != null) {
                builder.setHeader("eventId", payload.get("eventId").toString());
            } else {
                builder.setHeader("eventId", java.util.UUID.randomUUID().toString());
            }
            
            kafkaTemplate.send(builder.build());
            return ResponseEntity.ok(ApiResponse.success("Event republished successfully to " + targetTopic, "Successfully queued for retry"));
        } catch (Exception e) {
            log.error("Failed to retry DLQ event", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to republish event: " + e.getMessage()));
        }
    }

    @PostMapping("/refunds/{refundId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> retryRefund(@PathVariable java.util.UUID refundId) {
        try {
            com.fooddelivery.order.entity.Refund refund = refundRepository.findById(refundId)
                    .orElseThrow(() -> new IllegalArgumentException("Refund not found for ID: " + refundId));
            
            if (refund.getStatus() != com.fooddelivery.common.enums.RefundStatus.FAILED) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Refund can only be retried if status is FAILED. Current status: " + refund.getStatus()));
            }
            
            log.info("Admin manually retrying refund for ID: {}", refundId);
            
            refund.setStatus(com.fooddelivery.common.enums.RefundStatus.PROCESSING);
            refund.setFailureReason(null);
            refundRepository.save(refund);
            
            refundService.retryStuck(); // Triggers the sweeper logic to pick up the newly set PROCESSING refund
            
            return ResponseEntity.ok(ApiResponse.success("Refund process initiated successfully", "Successfully queued for retry"));
        } catch (Exception e) {
            log.error("Failed to retry refund {}", refundId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retry refund: " + e.getMessage()));
        }
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.springframework.data.domain.Page<Map<String, Object>>> getFailedRefunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<com.fooddelivery.order.entity.Refund> refundsPage = 
            refundRepository.findByStatus(com.fooddelivery.common.enums.RefundStatus.FAILED, pageable);
        
        org.springframework.data.domain.Page<Map<String, Object>> response = refundsPage.map(refund -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("refundId", refund.getId());
            map.put("orderId", refund.getOrderId());
            map.put("amount", refund.getAmount());
            map.put("status", refund.getStatus());
            map.put("errorMessage", refund.getFailureReason());
            map.put("createdAt", refund.getCreatedAt());

            orderRepository.findById(refund.getOrderId()).ifPresent(order -> {
                map.put("customerName", order.getCustomerId()); 
                map.put("restaurantId", order.getRestaurantId());
                map.put("orderStatus", order.getStatus());
                map.put("totalAmount", order.getTotalAmount());
            });
            return map;
        });
        
        return ResponseEntity.ok(response);
    }

    
}
