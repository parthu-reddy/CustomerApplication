package com.fooddelivery.order.controller;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.SupportTicket;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.SupportTicketRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.order.entity.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/internal/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
@lombok.RequiredArgsConstructor
public class AdminRefundController {

    private final SupportTicketRepository supportTicketRepository;

    private final IOrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final com.fooddelivery.common.service.RateLimitingService rateLimitingService;
    private final com.fooddelivery.order.refund.RefundService refundService;

    @GetMapping
    public ResponseEntity<Page<SupportTicket>> getTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SupportTicket> tickets;
        if (status != null && !status.isEmpty()) {
            tickets = supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.valueOf(status), pageable);
        } else {
            tickets = supportTicketRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return ResponseEntity.ok(tickets);
    }

    @PostMapping("/{ticketId}/review")
    @Transactional
    public ResponseEntity<SupportTicket> addReviewNotes(
            @PathVariable UUID ticketId,
            @RequestBody ReviewRequest request) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        
        if (request.notes() != null) {
            ticket.setResolutionNotes(ticket.getResolutionNotes() != null ? ticket.getResolutionNotes() + "\n" + request.notes() : request.notes());
        }
        ticket.setStatus(SupportTicket.TicketStatus.IN_REVIEW);
        supportTicketRepository.save(ticket);
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{ticketId}/resolve")
    @Transactional
    public ResponseEntity<SupportTicket> resolveTicket(
            @PathVariable UUID ticketId,
            @RequestBody ResolveRequest request,
            @RequestHeader("X-User-Id") UUID adminId) {
        
        // Phase 3: Limit 30 requests per minute
        io.github.bucket4j.Bucket bucket = rateLimitingService.resolveBucket("admin_refund:" + adminId.toString(), 30, 30, java.time.Duration.ofMinutes(1));
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).build();
        }

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

        if (ticket.getStatus() == SupportTicket.TicketStatus.RESOLVED || ticket.getStatus() == SupportTicket.TicketStatus.REJECTED) {
            throw new IllegalStateException("Ticket already processed");
        }

        ticket.setResolvedBy(adminId);
        ticket.setResolvedAt(LocalDateTime.now());
        ticket.setResolutionNotes(request.notes());

        if (request.approved()) {
            ticket.setStatus(SupportTicket.TicketStatus.RESOLVED);
            Order order = orderRepository.findById(ticket.getOrderId()).orElseThrow(() -> new IllegalArgumentException("Order not found"));
            BigDecimal refundAmount = ticket.getRefundAmount();
            if (request.overrideAmount() != null) {
                if (request.overrideAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Override amount must be greater than zero");
                }
                if (request.overrideAmount().compareTo(ticket.getRefundAmount()) > 0) {
                    throw new IllegalArgumentException("Override amount cannot be greater than original quote");
                }
                refundAmount = request.overrideAmount();
                ticket.setRefundAmount(refundAmount);
            }
            
            // Refunded quantities are no longer tracked on order_items: the column was removed and the
            // already-refunded quantity is derived from refund_items of COMPLETED refunds. The loop
            // that used to increment it lived here as dead commented-out code.
            java.util.List<com.fooddelivery.order.refund.RefundCommand.Item> refundCommandItems = new java.util.ArrayList<>();
            if (ticket.getRequestedRefundItems() != null && !ticket.getRequestedRefundItems().isBlank()) {
                try {
                    JsonNode itemsNode = objectMapper.readTree(ticket.getRequestedRefundItems());
                    if (itemsNode.isArray()) {
                        for (JsonNode itemNode : itemsNode) {
                            refundCommandItems.add(new com.fooddelivery.order.refund.RefundCommand.Item(
                                UUID.fromString(itemNode.get("itemId").asText()),
                                itemNode.get("quantity").asInt()
                            ));
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to parse requested refund items", e);
                }
            }

            com.fooddelivery.order.refund.RefundCommand cmd = com.fooddelivery.order.refund.RefundCommand.builder()
               .orderId(order.getId())
               .amount(refundAmount)
               .items(refundCommandItems.isEmpty() ? null : refundCommandItems)
               .faultType(request.faultType() != null ? com.fooddelivery.order.enums.FaultType.valueOf(request.faultType()) : com.fooddelivery.order.enums.FaultType.UNKNOWN)
               // No destination: RefundService routes from the payment method and intent state.
               // Hardcoding ORIGINAL_METHOD here pushed wallet-paid orders at a gateway that had
               // never taken the money, and threw REFUND_STATE_INVALID on every COD ticket.
               .initiatorType(com.fooddelivery.order.enums.InitiatorType.ADMIN)
               .initiatorId(adminId)
               .ticketId(ticketId)
               .reasonCode("ADMIN_RESOLUTION")
               .idempotencyKey("admin_resolve_" + ticketId)
               .build();
            refundService.request(cmd);
        } else {
            ticket.setStatus(SupportTicket.TicketStatus.REJECTED);
        }

        supportTicketRepository.save(ticket);
        return ResponseEntity.ok(ticket);
    }

    public record ReviewRequest(String notes) {}
    public record ResolveRequest(@io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED) boolean approved, String notes, String faultType, BigDecimal overrideAmount) {}

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<String> handleOptimisticLockingFailure(org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body("This ticket was recently updated by another administrator. Please refresh and try again.");
    }
}
