package com.fooddelivery.order.controller;

import com.fooddelivery.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
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
    private final com.fooddelivery.order.service.AdminDlqService adminDlqService;

    @PostMapping("/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> retryDlqEvent(@RequestBody Map<String, Object> payload, @RequestParam(required = false) String topic) {
        try {
            adminDlqService.retryDlqEvent(payload, topic);
            String targetTopic = topic != null && !topic.isEmpty() ? topic : com.fooddelivery.common.constants.KafkaConstants.TOPIC_ORDER_EVENTS;
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
            adminDlqService.retryRefund(refundId);
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
