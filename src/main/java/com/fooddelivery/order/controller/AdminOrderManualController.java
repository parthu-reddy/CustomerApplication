package com.fooddelivery.order.controller;

import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.SupportTicket;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.SupportTicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/admin/orders/intervention")
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class AdminOrderManualController {
    

    private final IOrderRepository orderRepository;
    private final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository;
    private final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator;
    private final SupportTicketRepository supportTicketRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.customer.dto.OrderResponse>> getOrdersRequiringIntervention(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        log.info("Fetching orders requiring manual intervention. Page: {}, Size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderRepository.findByDeliveryStatusOrderByCreatedAtDesc(com.fooddelivery.common.enums.DeliveryStatus.MANUAL_INTERVENTION_REQUIRED, pageable);
        return ResponseEntity.ok(com.fooddelivery.common.dto.PageResponseDto.of(orders.map(com.fooddelivery.customer.mapper.OrderMapper::mapToResponse)));
    }

    @PostMapping("/{orderId}/assign-driver")
    @PreAuthorize("hasRole(\'ADMIN\')")
    public ResponseEntity<ApiResponse<String>> assignDriver(@PathVariable UUID orderId, @RequestBody Map<String, String> payload) {
        String driverIdStr = payload.get("deliveryExecutiveId");
        if (driverIdStr == null || driverIdStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("deliveryExecutiveId is required"));
        }
        try {
            UUID driverId = UUID.fromString(driverIdStr);
            // Check current status
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Order order = orderOpt.get();
            if (order.getDeliveryStatus() != com.fooddelivery.common.enums.DeliveryStatus.MANUAL_INTERVENTION_REQUIRED) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Order is no longer in MANUAL_INTERVENTION_REQUIRED delivery status. Current delivery status: " + order.getDeliveryStatus()));
            }
            // Publish DELIVERY_EXECUTIVE_ASSIGNED event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("orderId", order.getId().toString());
            eventPayload.put("customerId", order.getCustomerId().toString());
            eventPayload.put("driverId", driverId.toString());
            eventPayload.put("timestamp", System.currentTimeMillis());
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonMessage = mapper.writeValueAsString(eventPayload);
            com.fooddelivery.common.outbox.entity.OutboxEventEntity outboxEvent = com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder()
                .id(java.util.UUID.randomUUID())
                .aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER)
                .aggregateId(order.getId().toString())
                .eventType(com.fooddelivery.common.constants.EventType.FORCE_ASSIGN_DRIVER)
                .payload(jsonMessage)
                .createdAt(java.time.LocalDateTime.now())
                .build();
            outboxEventRepository.save(outboxEvent);
            log.info("Admin successfully requested manual assignment of driver {} to order {}", driverId, order.getId());
            return ResponseEntity.ok(ApiResponse.success("Driver assignment requested successfully", "Operation successful"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid deliveryExecutiveId format"));
        } catch (Exception e) {
            log.error("Error during manual driver assignment", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to assign driver"));
        }
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole(\'ADMIN\')")
    public ResponseEntity<ApiResponse<String>> cancelOrder(@PathVariable UUID orderId, @RequestBody Map<String, String> payload) {
        String reason = payload.getOrDefault("reason", "Cancelled by Admin due to dispatch failure");
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Order order = orderOpt.get();
            if (order.getDeliveryStatus() != com.fooddelivery.common.enums.DeliveryStatus.MANUAL_INTERVENTION_REQUIRED) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Order is no longer in MANUAL_INTERVENTION_REQUIRED delivery status. Current delivery status: " + order.getDeliveryStatus()));
            }
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("orderId", order.getId().toString());
            eventPayload.put("customerId", order.getCustomerId().toString());
            eventPayload.put("reason", reason);
            eventPayload.put("timestamp", System.currentTimeMillis());
            // Use ORDER_CANCELLED_BY_ADMIN or standard cancellation
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonMessage = mapper.writeValueAsString(eventPayload);
            com.fooddelivery.common.outbox.entity.OutboxEventEntity outboxEvent = com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder()
                .id(java.util.UUID.randomUUID())
                .aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER)
                .aggregateId(order.getId().toString())
                .eventType(com.fooddelivery.common.constants.EventType.ORDER_CANCELLED_BY_ADMIN)
                .payload(jsonMessage)
                .createdAt(java.time.LocalDateTime.now())
                .build();
            outboxEventRepository.save(outboxEvent);
            log.info("Admin successfully requested manual cancellation for order {}. Reason: {}", order.getId(), reason);
            return ResponseEntity.ok(ApiResponse.success("Order cancellation requested successfully", "Operation successful"));
        } catch (Exception e) {
            log.error("Error during manual order cancellation", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to cancel order"));
        }
    }

    @PostMapping("/{orderId}/force-cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> forceCancelOrder(@PathVariable UUID orderId, @RequestBody Map<String, String> payload) {
        String reason = payload.getOrDefault("reason", "Force Cancelled by Admin");
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Order order = orderOpt.get();
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancellationReason(reason);
            orderRepository.save(order);
            log.info("Admin forcefully cancelled order {}. Reason: {}", order.getId(), reason);
            return ResponseEntity.ok(ApiResponse.success("Order forcefully cancelled", "Operation successful"));
        } catch (Exception e) {
            log.error("Error during force cancel", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to force cancel order"));
        }
    }



    // ==================== SUPPORT TICKET MANAGEMENT ====================

    @GetMapping("/support-tickets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.fooddelivery.common.dto.PageResponseDto<SupportTicket>> getOpenSupportTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "OPEN") String status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SupportTicket> tickets;
        try {
            SupportTicket.TicketStatus ticketStatus = SupportTicket.TicketStatus.valueOf(status.toUpperCase());
            tickets = supportTicketRepository.findByStatusOrderByCreatedAtDesc(ticketStatus, pageable);
        } catch (IllegalArgumentException e) {
            tickets = supportTicketRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return ResponseEntity.ok(com.fooddelivery.common.dto.PageResponseDto.of(tickets));
    }

    @PostMapping("/support-tickets/{ticketId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> resolveSupportTicket(
            @PathVariable UUID ticketId,
            java.security.Principal principal,
            @RequestBody Map<String, String> payload) {
        Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SupportTicket ticket = ticketOpt.get();
        if (ticket.getStatus() != SupportTicket.TicketStatus.OPEN && ticket.getStatus() != SupportTicket.TicketStatus.IN_REVIEW) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Ticket is already " + ticket.getStatus()));
        }
        String action = payload.getOrDefault("action", "RESOLVED").toUpperCase();
        String notes = payload.get("notes");

        if ("REJECTED".equals(action)) {
            ticket.setStatus(SupportTicket.TicketStatus.REJECTED);
        } else {
            ticket.setStatus(SupportTicket.TicketStatus.RESOLVED);
        }
        ticket.setResolutionNotes(notes);
        ticket.setResolvedBy(UUID.fromString(principal.getName()));
        ticket.setResolvedAt(LocalDateTime.now());
        supportTicketRepository.save(ticket);

        log.info("Admin {} {} support ticket {} for order {}. Notes: {}", principal.getName(), action, ticketId, ticket.getOrderId(), notes);
        return ResponseEntity.ok(ApiResponse.success("Support ticket " + action.toLowerCase() + " successfully", "Operation successful"));
    }

    
}
