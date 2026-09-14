package com.fooddelivery.order.controller;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.common.enums.OrderStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/internal/orders")
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class InternalOrderController {

    private final IOrderRepository orderRepository;
    private final com.fooddelivery.order.service.OrderSagaOrchestrator orderSagaOrchestrator;
    private final com.fooddelivery.common.service.RateLimitingService rateLimitingService;
    private final com.fooddelivery.order.refund.RefundService refundService;

    @GetMapping("/driver/{driverId}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'DELIVERY')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<org.springframework.data.domain.Page<Order>> getActiveOrdersForDriver(
            @PathVariable UUID driverId,
            @org.springframework.data.web.PageableDefault(size = 50) org.springframework.data.domain.Pageable pageable) {
        log.info("Fetching active orders for driver {}", driverId);
        List<OrderStatus> cancelledStatuses = Arrays.asList(OrderStatus.CANCELLED, OrderStatus.CANCELLED_BY_RESTAURANT);
        List<com.fooddelivery.common.enums.DeliveryStatus> terminalDeliveryStatuses = java.util.List.of(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED, com.fooddelivery.common.enums.DeliveryStatus.FAILED, com.fooddelivery.common.enums.DeliveryStatus.CANCELLED);
        log.info("findActiveOrdersForDriver parameters: driverId={}, cancelledStatuses={}, terminalDeliveryStatuses={}", driverId, cancelledStatuses, terminalDeliveryStatuses);
        org.springframework.data.domain.Page<Order> activeOrders = orderRepository.findActiveOrdersForDriver(driverId, cancelledStatuses, terminalDeliveryStatuses, pageable);
        log.info("Decision: active orders found count={}", activeOrders.getTotalElements());
        if (activeOrders.isEmpty()) {
            log.info("Decision: returning empty orders list. Check if deliveryExecutiveId is matching and status is not terminal.");
        } else {
            activeOrders.getContent().forEach(order -> log.info("Decision: Returning Order ID {}, Status {}, DeliveryStatus {}", order.getId(), order.getStatus(), order.getDeliveryStatus()));
        }
        return ResponseEntity.ok(activeOrders);
    }

    @GetMapping("/driver/{driverId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'DELIVERY')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<org.springframework.data.domain.Page<Order>> getOrderHistoryForDriver(
            @PathVariable UUID driverId, 
            @RequestParam(required = false) String date,
            @org.springframework.data.web.PageableDefault(size = 50) org.springframework.data.domain.Pageable pageable) {
        log.info("Fetching order history for driver {} for date {}", driverId, date);
        LocalDate queryDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime startOfDay = queryDate.atStartOfDay();
        LocalDateTime endOfDay = queryDate.atTime(LocalTime.MAX);
        List<OrderStatus> cancelledStatuses = Arrays.asList(OrderStatus.CANCELLED, OrderStatus.CANCELLED_BY_RESTAURANT);
        List<com.fooddelivery.common.enums.DeliveryStatus> terminalDeliveryStatuses = java.util.List.of(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED, com.fooddelivery.common.enums.DeliveryStatus.FAILED, com.fooddelivery.common.enums.DeliveryStatus.CANCELLED);
        log.info("findHistoryOrdersForDriver parameters: driverId={}, start={}, end={}, cancelledStatuses={}, terminalDeliveryStatuses={}", driverId, startOfDay, endOfDay, cancelledStatuses, terminalDeliveryStatuses);
        org.springframework.data.domain.Page<Order> history = orderRepository.findHistoryOrdersForDriver(driverId, cancelledStatuses, terminalDeliveryStatuses, startOfDay, endOfDay, pageable);
        log.info("Decision: order history found count={}", history.getTotalElements());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasAnyRole(\'ADMIN\', \'DELIVERY\')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<Order>> getUnassignedOrders() {
        log.info("Fetching unassigned orders for broadcast");
        List<OrderStatus> dispatchableStatuses = Arrays.asList(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP);
        log.info("findByStatusInAndDeliveryExecutiveIdIsNull parameters: dispatchableStatuses={}", dispatchableStatuses);
        List<Order> unassignedOrders = orderRepository.findByStatusInAndDeliveryExecutiveIdIsNull(dispatchableStatuses, org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
        log.info("Decision: unassigned orders found count={}", unassignedOrders.size());
        return ResponseEntity.ok(unassignedOrders);
    }

    /**
     * Everything a reviewing service needs to decide whether a review of this order is allowed.
     *
     * <p>Answers "was this target on this customer's delivered order" in one call. The previous
     * design asked three separate services whether each target existed, which is both the wrong
     * question — existence is not eligibility — and unanswerable: those endpoints require
     * {@code SERVICE}/{@code RESTAURANT}/{@code ADMIN} and the identity propagated on a review
     * request is the customer's.
     *
     * <p>{@code isOrderCustomer}, not {@code isOrderParticipant}: the payload carries the customer's
     * own name, and the driver and outlet owner must not read it.
     */
    @GetMapping("/{orderId}/review-context")
    @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN') or @orderSecurityHelper.isOrderCustomer(#orderId, authentication.name)")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<com.fooddelivery.common.dto.ApiResponse<com.fooddelivery.common.dto.order.OrderReviewContextDto>> getOrderReviewContext(
            @PathVariable UUID orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    // orderItems is LAZY; this read has to happen inside the transaction this
                    // method opens, not after the response is serialised.
                    List<com.fooddelivery.common.dto.order.OrderReviewItemDto> items =
                            order.getOrderItems().stream()
                                    .map(item -> com.fooddelivery.common.dto.order.OrderReviewItemDto.builder()
                                            .menuItemId(item.getMenuItemId())
                                            .name(item.getName())
                                            .build())
                                    .toList();

                    com.fooddelivery.common.dto.order.OrderReviewContextDto context =
                            com.fooddelivery.common.dto.order.OrderReviewContextDto.builder()
                                    .orderId(order.getId())
                                    .customerId(order.getCustomerId())
                                    .customerName(order.getCustomerName())
                                    .restaurantId(order.getRestaurantId())
                                    .restaurantName(order.getRestaurantName())
                                    .deliveryExecutiveId(order.getDeliveryExecutiveId())
                                    .deliveryStatus(order.getDeliveryStatus())
                                    .deliveredAt(order.getDeliveredAt())
                                    .items(items)
                                    .build();

                    return ResponseEntity.ok(
                            com.fooddelivery.common.dto.ApiResponse.success(context, "Review context retrieved"));
                })
                .orElseGet(() -> ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body(com.fooddelivery.common.dto.ApiResponse.error("Order " + orderId + " not found")));
    }

    @GetMapping("/{orderId}/participants")
    @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN') or @orderSecurityHelper.isOrderParticipant(#orderId, authentication.name)")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<String>> getOrderParticipants(@PathVariable UUID orderId) {
        log.debug("Fetching authorized participants for order {}", orderId);
        return orderRepository.findById(orderId).map(order -> {
            List<String> participants = new java.util.ArrayList<>();
            if (order.getCustomerId() != null) participants.add(order.getCustomerId().toString());
            if (order.getRestaurantId() != null) participants.add(order.getRestaurantId().toString());
            if (order.getDeliveryExecutiveId() != null) participants.add(order.getDeliveryExecutiveId().toString());
            return ResponseEntity.ok(participants);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns the order's OTPs and payment method for service-to-service calls.
     *
     * <p>Used by delivery-service when a force-assign occurs after the Redis dispatch
     * payload has been cleaned up by TerminalStateStrategy.
     */
    @GetMapping("/{orderId}/dispatch-details")
    @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<java.util.Map<String, String>> getOrderDispatchDetails(@PathVariable UUID orderId) {
        log.info("Fetching dispatch details (OTPs) for order {}", orderId);
        return orderRepository.findById(orderId).map(order -> {
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("pickupOtp", order.getPickupOtp());
            details.put("deliveryOtp", order.getOtp());
            details.put("paymentMethod", order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null);
            return ResponseEntity.ok(details);
        }).orElse(ResponseEntity.notFound().build());
    }

}
