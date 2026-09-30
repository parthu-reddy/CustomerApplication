package com.fooddelivery.order.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.SupportTicket;
import com.fooddelivery.order.dto.AdminManualAssignmentRequest;
import com.fooddelivery.order.dto.AdminManualCancellationRequest;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.SupportTicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/admin/orders/intervention")
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional
@org.springframework.validation.annotation.Validated
public class AdminOrderManualController {
    

    private final IOrderRepository orderRepository;
    private final com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxEventRepository;
    private final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator;
    private final SupportTicketRepository supportTicketRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.customer.dto.OrderResponse>> getOrdersRequiringIntervention(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        log.info("Fetching orders requiring manual intervention. Page: {}, Size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderRepository.findByDeliveryStatusOrderByCreatedAtDesc(com.fooddelivery.common.enums.DeliveryStatus.MANUAL_INTERVENTION_REQUIRED, pageable);
        return ResponseEntity.ok(com.fooddelivery.common.dto.PageResponseDto.of(orders.map(com.fooddelivery.customer.mapper.OrderMapper::mapToResponse)));
    }

    @PostMapping("/{orderId}/assign-driver")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> assignDriver(
            @PathVariable UUID orderId,
            java.security.Principal principal,
            @RequestHeader("Idempotency-Key")
            @NotBlank @Size(min = 8, max = 80)
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,79}") String idempotencyKey,
            @Valid @RequestBody AdminManualAssignmentRequest request) {
        UUID actorId = authenticatedAdmin(principal);
        String operationId = operationId("assign", orderId, actorId, idempotencyKey);

        Order order = orderRepository.findLockedById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        Optional<com.fooddelivery.common.outbox.entity.OutboxEventEntity> replay =
                outboxEventRepository.findByIdempotencyKey(operationId);
        if (replay.isPresent()) {
            if (isMatchingAssignmentReplay(replay.get(), orderId, actorId, request, operationId)) {
                return ResponseEntity.ok(ApiResponse.success(
                        "Driver assignment was already requested", "Idempotent replay"));
            }
            return conflict("This idempotency key was already used for a different assignment request.");
        }

        if (order.getDeliveryStatus() != com.fooddelivery.common.enums.DeliveryStatus.MANUAL_INTERVENTION_REQUIRED) {
            return conflict("Order is no longer awaiting manual dispatch intervention.");
        }
        if (hasPendingManualIntervention(order)) {
            return conflict("Another manual intervention is still being processed for this order.");
        }

        String reason = request.reason().trim();
        order.setManualInterventionOperationId(operationId);
        order.setManualInterventionRequestedDriverId(request.deliveryExecutiveId());
        order.setManualInterventionRequestedBy(actorId);
        order.setManualInterventionReason(reason);
        order.setManualInterventionRequestedAt(Instant.now());
        order.setManualInterventionFailureCode(null);
        order.setManualInterventionFailedAt(null);
        orderRepository.save(order);

        com.fooddelivery.common.event.ForceAssignDriverEvent event =
                com.fooddelivery.common.event.ForceAssignDriverEvent.builder()
                        .orderId(order.getId().toString())
                        .customerId(order.getCustomerId() == null ? null : order.getCustomerId().toString())
                        .driverId(request.deliveryExecutiveId().toString())
                        .operationId(operationId)
                        .actorId(actorId.toString())
                        .reason(reason)
                        .dispatchCityId(order.getDispatchCityId())
                        .timestamp(System.currentTimeMillis())
                        .build();
        outboxEventRepository.save(com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder()
                .aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER)
                .aggregateId(order.getId().toString())
                .eventType(com.fooddelivery.common.constants.EventType.FORCE_ASSIGN_DRIVER)
                .idempotencyKey(operationId)
                .payload(write(event))
                .createdAt(Instant.now())
                .build());

        log.info("MANUAL_ASSIGNMENT_REQUESTED orderId={} driverId={} actorId={} operationId={}",
                order.getId(), request.deliveryExecutiveId(), actorId, operationId);
        return ResponseEntity.ok(ApiResponse.success(
                "Driver assignment requested successfully", "Operation successful"));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> cancelOrder(
            @PathVariable UUID orderId,
            java.security.Principal principal,
            @RequestHeader("Idempotency-Key")
            @NotBlank @Size(min = 8, max = 80)
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,79}") String idempotencyKey,
            @Valid @RequestBody AdminManualCancellationRequest request) {
        return requestCancellation(orderId, principal, idempotencyKey, request, "cancel");
    }

    @PostMapping("/{orderId}/force-cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> forceCancelOrder(
            @PathVariable UUID orderId,
            java.security.Principal principal,
            @RequestHeader("Idempotency-Key")
            @NotBlank @Size(min = 8, max = 80)
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,79}") String idempotencyKey,
            @Valid @RequestBody AdminManualCancellationRequest request) {
        // A direct row mutation used to skip refunds, notifications, and the Delivery terminal
        // cleanup. It is deliberately the same audited cancellation command as /cancel now.
        return requestCancellation(orderId, principal, idempotencyKey, request, "force-cancel");
    }

    private ResponseEntity<ApiResponse<String>> requestCancellation(
            UUID orderId,
            java.security.Principal principal,
            String idempotencyKey,
            AdminManualCancellationRequest request,
            String action) {
        UUID actorId = authenticatedAdmin(principal);
        String operationId = operationId(action, orderId, actorId, idempotencyKey);

        Order order = orderRepository.findLockedById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        Optional<com.fooddelivery.common.outbox.entity.OutboxEventEntity> replay =
                outboxEventRepository.findByIdempotencyKey(operationId);
        if (replay.isPresent()) {
            if (isMatchingCancellationReplay(replay.get(), orderId, actorId, request, operationId)) {
                return ResponseEntity.ok(ApiResponse.success(
                        "Order cancellation was already requested", "Idempotent replay"));
            }
            return conflict("This idempotency key was already used for a different cancellation request.");
        }

        if (order.getDeliveryStatus() != com.fooddelivery.common.enums.DeliveryStatus.MANUAL_INTERVENTION_REQUIRED) {
            return conflict("Order is no longer awaiting manual dispatch intervention.");
        }
        if (hasPendingManualIntervention(order)) {
            return conflict("Another manual intervention is still being processed for this order.");
        }

        String reason = request.reason().trim();
        order.setManualInterventionOperationId(operationId);
        order.setManualInterventionRequestedDriverId(null);
        order.setManualInterventionRequestedBy(actorId);
        order.setManualInterventionReason(reason);
        order.setManualInterventionRequestedAt(Instant.now());
        order.setManualInterventionFailureCode(null);
        order.setManualInterventionFailedAt(null);
        orderRepository.save(order);

        com.fooddelivery.common.event.OrderCancelledByAdminEvent event =
                com.fooddelivery.common.event.OrderCancelledByAdminEvent.builder()
                        .orderId(order.getId().toString())
                        .customerId(order.getCustomerId() == null ? null : order.getCustomerId().toString())
                        .reason(reason)
                        .operationId(operationId)
                        .actorId(actorId.toString())
                        .timestamp(System.currentTimeMillis())
                        .build();
        outboxEventRepository.save(com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder()
                .aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER)
                .aggregateId(order.getId().toString())
                .eventType(com.fooddelivery.common.constants.EventType.ORDER_CANCELLED_BY_ADMIN)
                .idempotencyKey(operationId)
                .payload(write(event))
                .createdAt(Instant.now())
                .build());

        log.info("MANUAL_CANCELLATION_REQUESTED orderId={} actorId={} operationId={}",
                order.getId(), actorId, operationId);
        return ResponseEntity.ok(ApiResponse.success(
                "Order cancellation requested successfully", "Operation successful"));
    }

    private UUID authenticatedAdmin(java.security.Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An authenticated administrator is required.");
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException invalidIdentity) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The authenticated administrator identity is invalid.");
        }
    }

    /**
     * A manual operation is a cross-service command. Replacing its operation id while Delivery is
     * validating the older command lets the older rider assignment commit after Customer has
     * switched to a newer decision. The exact idempotent replay above remains allowed; a new
     * action becomes available once Delivery has either completed the operation or recorded its
     * durable rejection result.
     */
    private boolean hasPendingManualIntervention(Order order) {
        return order.getManualInterventionOperationId() != null
                && !order.getManualInterventionOperationId().isBlank()
                && order.getManualInterventionFailureCode() == null;
    }

    private String operationId(String action, UUID orderId, UUID actorId, String idempotencyKey) {
        return "admin-manual:" + action + ":" + orderId + ":" + actorId + ":" + idempotencyKey.trim();
    }

    private String write(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (com.fasterxml.jackson.core.JsonProcessingException serializationFailure) {
            throw new IllegalStateException("Unable to create the manual intervention audit event.", serializationFailure);
        }
    }

    private boolean isMatchingAssignmentReplay(
            com.fooddelivery.common.outbox.entity.OutboxEventEntity event,
            UUID orderId,
            UUID actorId,
            AdminManualAssignmentRequest request,
            String operationId) {
        if (event.getEventType() != com.fooddelivery.common.constants.EventType.FORCE_ASSIGN_DRIVER) {
            return false;
        }
        try {
            com.fooddelivery.common.event.ForceAssignDriverEvent payload = objectMapper.readValue(
                    event.getPayload(), com.fooddelivery.common.event.ForceAssignDriverEvent.class);
            return orderId.toString().equals(payload.getOrderId())
                    && actorId.toString().equals(payload.getActorId())
                    && request.deliveryExecutiveId().toString().equals(payload.getDriverId())
                    && request.reason().trim().equals(payload.getReason())
                    && operationId.equals(payload.getOperationId());
        } catch (Exception unreadablePayload) {
            log.error("Manual assignment idempotency payload could not be read. eventId={}", event.getId(),
                    unreadablePayload);
            return false;
        }
    }

    private boolean isMatchingCancellationReplay(
            com.fooddelivery.common.outbox.entity.OutboxEventEntity event,
            UUID orderId,
            UUID actorId,
            AdminManualCancellationRequest request,
            String operationId) {
        if (event.getEventType() != com.fooddelivery.common.constants.EventType.ORDER_CANCELLED_BY_ADMIN) {
            return false;
        }
        try {
            com.fooddelivery.common.event.OrderCancelledByAdminEvent payload = objectMapper.readValue(
                    event.getPayload(), com.fooddelivery.common.event.OrderCancelledByAdminEvent.class);
            return orderId.toString().equals(payload.getOrderId())
                    && actorId.toString().equals(payload.getActorId())
                    && request.reason().trim().equals(payload.getReason())
                    && operationId.equals(payload.getOperationId());
        } catch (Exception unreadablePayload) {
            log.error("Manual cancellation idempotency payload could not be read. eventId={}", event.getId(),
                    unreadablePayload);
            return false;
        }
    }

    private ResponseEntity<ApiResponse<String>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(message, "MANUAL_INTERVENTION_CONFLICT"));
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

}
