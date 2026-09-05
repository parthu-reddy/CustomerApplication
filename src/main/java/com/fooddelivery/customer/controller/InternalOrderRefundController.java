package com.fooddelivery.customer.controller;

import com.fooddelivery.order.refund.RefundCommand;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.refund.RefundView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/internal/orders")
@lombok.RequiredArgsConstructor
public class InternalOrderRefundController {

    private final RefundService refundService;

    @PostMapping("/{orderId}/partial-refund")
    @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
    public ResponseEntity<RefundView> initiatePartialRefund(
            @PathVariable("orderId") UUID orderId,
            @RequestBody RefundCommand command) {
        
        // Ensure the order ID from path matches command (if provided)
        if (command.getOrderId() == null) {
            command.setOrderId(orderId);
        } else if (!command.getOrderId().equals(orderId)) {
            throw new IllegalArgumentException("Path orderId does not match body orderId");
        }
        
        if (command.getInitiatorType() == null) {
            command.setInitiatorType(com.fooddelivery.order.enums.InitiatorType.SYSTEM);
        }
        
        RefundView view = refundService.request(command);
        return ResponseEntity.ok(view);
    }
}
