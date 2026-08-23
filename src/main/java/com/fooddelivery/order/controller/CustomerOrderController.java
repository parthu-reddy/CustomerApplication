package com.fooddelivery.order.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.SupportTicket;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.SupportTicketRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/orders")
@lombok.extern.slf4j.Slf4j
public class CustomerOrderController {

    private final IOrderRepository orderRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final com.fooddelivery.common.service.RateLimitingService rateLimitingService;

    public CustomerOrderController(IOrderRepository orderRepository, SupportTicketRepository supportTicketRepository, com.fooddelivery.common.service.RateLimitingService rateLimitingService) {
        this.orderRepository = orderRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.rateLimitingService = rateLimitingService;
    }

    private boolean isRateLimited(String clientKey) {
        if (clientKey == null || clientKey.isBlank() || clientKey.equals("unknown")) return true;
        io.github.bucket4j.Bucket bucket = rateLimitingService.resolveBucket("refund:" + clientKey, 10, 10, java.time.Duration.ofMinutes(1));
        return !bucket.tryConsume(1);
    }

    /**
     * Customer requests a refund for an order that has already been delivered,
     * due to missing items, food quality issues, etc.
     */
    @PostMapping("/{orderId}/refund-request")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<String>> requestPostDeliveryRefund(
            Principal principal,
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> payload) {

        UUID customerId = UUID.fromString(principal.getName());

        if (isRateLimited(customerId.toString())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).build();
        }

        String reason = payload.get("reason");
        if (reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>error("A reason for the refund request is required"));
        }

        return orderRepository.findById(orderId).map(order -> {
            // Verify the customer owns this order
            if (!order.getCustomerId().equals(customerId)) {
                log.warn("IDOR attempt: Customer {} tried to request refund for order {} owned by {}", customerId, orderId, order.getCustomerId());
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body(ApiResponse.<String>error("You are not authorized to request a refund for this order."));
            }

            // Verify order is delivered
            if (order.getDeliveryStatus() != com.fooddelivery.common.enums.DeliveryStatus.DELIVERED) {
                return ResponseEntity.badRequest().body(ApiResponse.<String>error("Refund requests can only be made for delivered orders through this channel."));
            }

            // Prevent duplicate open tickets for the same order
            if (supportTicketRepository.existsByOrderIdAndCustomerIdAndStatus(orderId, customerId, SupportTicket.TicketStatus.OPEN)) {
                return ResponseEntity.badRequest().body(ApiResponse.<String>error("You already have an open refund request for this order. Please wait for our team to review it."));
            }

            // Persist the support ticket
            SupportTicket ticket = new SupportTicket();
            ticket.setOrderId(orderId);
            ticket.setCustomerId(customerId);
            ticket.setReason(reason.trim());
            ticket.setStatus(SupportTicket.TicketStatus.OPEN);
            supportTicketRepository.save(ticket);

            log.info("Customer {} requested post-delivery refund for order {}. Ticket ID: {}. Reason: {}", customerId, orderId, ticket.getId(), reason);

            return ResponseEntity.ok(ApiResponse.success(
                    "Refund request submitted successfully. Our support team will review it shortly.",
                    "Successfully requested refund"));
        }).orElseGet(() -> ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(ApiResponse.<String>error("Order not found")));
    }
}
