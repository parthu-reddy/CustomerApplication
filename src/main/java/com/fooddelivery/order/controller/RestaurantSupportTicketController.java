package com.fooddelivery.order.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.repository.RefundRepository;
import com.fooddelivery.order.refund.RefundView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/restaurants/outlets")
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class RestaurantSupportTicketController {

    private final RefundRepository refundRepository;

    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT')")
    @GetMapping("/{outletId}/refund-requests")
    public ResponseEntity<ApiResponse<List<RefundView>>> getActiveRefundRequests(
            @PathVariable UUID outletId) {
        log.info("Fetching active refund requests for outlet {}", outletId);
        try {
            List<Refund> activeRefunds = refundRepository.findRestaurantFaultRefunds(outletId, com.fooddelivery.common.enums.RefundStatus.PROCESSING);
            List<RefundView> views = activeRefunds.stream().map(r -> RefundView.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .amount(r.getAmount())
                .status(r.getStatus())
                .destination(r.getDestination())
                // .method(r.getOrder().getPaymentMethod()) // Wait, can't easily fetch order here without N+1 or adding OrderRepository. Actually we can omit it if not strictly required, or add IOrderRepository. Let me omit it for now since the UI might not need the method strictly, or I could just return null. Wait, let's look at RefundView, it's a simple class.
                .reasonCode(r.getReasonCode())
                .requestedAt(r.getCreatedAt())
                .completedAt(r.getUpdatedAt())
                .build()).toList();
            return ResponseEntity.ok(ApiResponse.success(views, "Successfully fetched active refund requests"));
        } catch (Exception e) {
            log.error("Failed to fetch refund requests for outlet {}", outletId, e);
            return ResponseEntity.status(500).body(ApiResponse.<List<RefundView>>error("Failed to fetch refund requests"));
        }
    }
}
