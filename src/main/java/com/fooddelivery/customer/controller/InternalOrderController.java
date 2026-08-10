package com.fooddelivery.customer.controller;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/orders")
public class InternalOrderController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InternalOrderController.class);

    private final IOrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;

    public InternalOrderController(IOrderRepository orderRepository, OrderSagaOrchestrator orderSagaOrchestrator) {
        this.orderRepository = orderRepository;
        this.orderSagaOrchestrator = orderSagaOrchestrator;
    }

    @PostMapping("/{orderId}/partial-refund")
    public ResponseEntity<Map<String, String>> initiatePartialRefund(
            @PathVariable("orderId") UUID orderId,
            @RequestBody Map<String, String> payload) {
        
        String amountStr = payload.get("amount");
        if (amountStr == null || amountStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Amount is required"));
        }

        BigDecimal refundAmount;
        try {
            refundAmount = new BigDecimal(amountStr);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid amount format"));
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid refund amount"));
        }

        BigDecimal alreadyRefunded = order.getRefundedAmount() != null ? order.getRefundedAmount() : BigDecimal.ZERO;
        BigDecimal remainingAmount = order.getTotalAmount().subtract(alreadyRefunded);

        if (refundAmount.compareTo(remainingAmount) > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refund amount exceeds the refundable balance of the order."));
        }

        order.setRefundedAmount(alreadyRefunded.add(refundAmount));
        orderRepository.save(order);
        
        log.info("Processing internal partial refund of {} for order {}", refundAmount, orderId);
        orderSagaOrchestrator.processPartialRefund(order, refundAmount);

        return ResponseEntity.ok(Map.of("message", "Partial refund initiated successfully"));
    }
}
