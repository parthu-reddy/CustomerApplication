package com.fooddelivery.customer.controller;

import com.fooddelivery.order.refund.RefundCommand;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.refund.RefundView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/admin/refunds")
@lombok.RequiredArgsConstructor
public class AdminRefundCommandController {

    private final RefundService refundService;

    @PostMapping("/request")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RefundView> requestRefund(@RequestBody RefundCommand command) {
        if (command.getInitiatorType() == null) {
            command.setInitiatorType(com.fooddelivery.order.enums.InitiatorType.ADMIN);
        }
        RefundView view = refundService.request(command);
        return ResponseEntity.ok(view);
    }
}
