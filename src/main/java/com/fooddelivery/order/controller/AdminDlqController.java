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
    public ResponseEntity<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.order.dto.FailedRefundDto>> getFailedRefunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<com.fooddelivery.order.entity.Refund> refundsPage = 
            refundRepository.findByStatus(com.fooddelivery.common.enums.RefundStatus.FAILED, pageable);
        
        java.util.List<com.fooddelivery.order.dto.FailedRefundDto> content = refundsPage.stream().map(refund -> {
            com.fooddelivery.order.dto.FailedRefundDto dto = com.fooddelivery.order.dto.FailedRefundDto.builder()
                .refundId(refund.getId())
                .orderId(refund.getOrderId())
                .amount(refund.getAmount())
                .status(refund.getStatus())
                .errorMessage(refund.getFailureReason())
                .createdAt(refund.getCreatedAt())
                .build();

            orderRepository.findById(refund.getOrderId()).ifPresent(order -> {
                dto.setCustomerName(order.getCustomerId()); 
                dto.setRestaurantId(order.getRestaurantId());
                dto.setOrderStatus(order.getStatus());
                dto.setTotalAmount(order.getTotalAmount());
            });
            return dto;
        }).collect(java.util.stream.Collectors.toList());
        
        com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.order.dto.FailedRefundDto> response = 
            com.fooddelivery.common.dto.PageResponseDto.<com.fooddelivery.order.dto.FailedRefundDto>builder()
                .content(content)
                .number(refundsPage.getNumber())
                .size(refundsPage.getSize())
                .totalElements(refundsPage.getTotalElements())
                .totalPages(refundsPage.getTotalPages())
                .last(refundsPage.isLast())
                .first(refundsPage.isFirst())
                .numberOfElements(refundsPage.getNumberOfElements())
                .empty(refundsPage.isEmpty())
                .build();
        
        return ResponseEntity.ok(response);
    }
}
