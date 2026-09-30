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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/internal/admin/refunds")
@PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
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
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<SupportTicket> resolveTicket(
            @PathVariable UUID ticketId,
            @RequestBody ResolveRequest request,
            Authentication authentication) {
        UUID adminId = authenticatedAdminId(authentication);
        
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

        if (request.approved()) {
            Order order = orderRepository.findById(ticket.getOrderId()).orElseThrow(() -> new IllegalArgumentException("Order not found"));
            java.util.List<com.fooddelivery.order.refund.RefundCommand.Item> refundCommandItems = parseRequestedItems(ticket);
            BigDecimal quotedAmount = validatedTicketQuote(ticket, order, refundCommandItems);
            BigDecimal refundAmount = quotedAmount;
            if (request.overrideAmount() != null) {
                BigDecimal overrideAmount = validatedAmount(request.overrideAmount(), "Override amount must be greater than zero");
                if (overrideAmount.compareTo(quotedAmount) > 0) {
                    throw new IllegalArgumentException("Override amount cannot be greater than original quote");
                }
                refundAmount = overrideAmount;
            }
            
            com.fooddelivery.order.refund.RefundCommand cmd = com.fooddelivery.order.refund.RefundCommand.builder()
               .orderId(order.getId())
               .amount(refundAmount)
               .items(refundCommandItems.isEmpty() ? null : refundCommandItems)
               .faultType(request.faultType() != null ? com.fooddelivery.order.enums.FaultType.valueOf(request.faultType()) : com.fooddelivery.order.enums.FaultType.UNKNOWN)
               .source(com.fooddelivery.order.enums.RefundSource.CUSTOMER_TICKET)
               // No destination: RefundService routes from the payment method and intent state.
               // Hardcoding ORIGINAL_METHOD here pushed wallet-paid orders at a gateway that had
               // never taken the money.
               .initiatorType(com.fooddelivery.order.enums.InitiatorType.ADMIN)
               .initiatorId(adminId)
               .ticketId(ticketId)
               .reasonCode("ADMIN_RESOLUTION")
               .idempotencyKey("admin_resolve_" + ticketId)
               .build();
            refundService.request(cmd);
            ticket.setRefundAmount(refundAmount);
            ticket.setStatus(SupportTicket.TicketStatus.RESOLVED);
        } else {
            ticket.setStatus(SupportTicket.TicketStatus.REJECTED);
        }

        ticket.setResolvedBy(adminId);
        ticket.setResolvedAt(Instant.now());
        ticket.setResolutionNotes(request.notes());
        supportTicketRepository.save(ticket);
        return ResponseEntity.ok(ticket);
    }

    private UUID authenticatedAdminId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authenticated administrator is required");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Authenticated administrator identity is invalid");
        }
    }

    private java.util.List<com.fooddelivery.order.refund.RefundCommand.Item> parseRequestedItems(SupportTicket ticket) {
        if (ticket.getRequestedRefundItems() == null || ticket.getRequestedRefundItems().isBlank()) {
            return java.util.List.of();
        }
        try {
            JsonNode itemsNode = objectMapper.readTree(ticket.getRequestedRefundItems());
            if (!itemsNode.isArray()) {
                throw new IllegalStateException("Stored refund items are invalid");
            }
            java.util.List<com.fooddelivery.order.refund.RefundCommand.Item> items = new java.util.ArrayList<>();
            for (JsonNode itemNode : itemsNode) {
                JsonNode itemId = itemNode.get("itemId");
                JsonNode quantity = itemNode.get("quantity");
                if (itemId == null || quantity == null || !quantity.canConvertToInt()) {
                    throw new IllegalStateException("Stored refund items are invalid");
                }
                items.add(new com.fooddelivery.order.refund.RefundCommand.Item(
                        UUID.fromString(itemId.asText()), quantity.asInt()));
            }
            return items;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Stored refund items are invalid", ex);
        }
    }

    private BigDecimal validatedTicketQuote(SupportTicket ticket, Order order,
                                            java.util.List<com.fooddelivery.order.refund.RefundCommand.Item> items) {
        BigDecimal quotedAmount = validatedAmount(ticket.getRefundAmount(), "Stored refund quote is invalid");
        if (items.isEmpty()) {
            BigDecimal fullOrderAmount = validatedAmount(order.getTotalAmount(), "Order total is invalid");
            if (quotedAmount.compareTo(fullOrderAmount) != 0) {
                throw new IllegalStateException("Stored refund quote does not match the order total");
            }
        } else {
            BigDecimal freshQuote = refundService.quote(order.getId(), items);
            if (quotedAmount.compareTo(freshQuote) != 0) {
                throw new IllegalStateException("Stored refund quote is stale or invalid");
            }
        }
        return quotedAmount;
    }

    private BigDecimal validatedAmount(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }

    public record ReviewRequest(String notes) {}
    public record ResolveRequest(@io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED) boolean approved, String notes, String faultType, BigDecimal overrideAmount) {}

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<String> handleOptimisticLockingFailure(org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body("This ticket was recently updated by another administrator. Please refresh and try again.");
    }
}
