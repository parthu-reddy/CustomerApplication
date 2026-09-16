package com.fooddelivery.money.controller;

import com.fooddelivery.common.dto.order.RestaurantOrderEarnings;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.common.security.money.MoneyAccessPolicy;
import com.fooddelivery.common.security.money.MoneyOwnerType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/money/restaurant")
@lombok.RequiredArgsConstructor



public class RestaurantMoneyController {

    private final IOrderRepository orderRepository;
    private final MoneyAccessPolicy moneyAccessPolicy;
    private final com.fooddelivery.order.repository.RefundRepository refundRepository;
    private final com.fooddelivery.customer.service.money.RestaurantSummaryService restaurantSummaryService;
    private final com.fooddelivery.customer.client.LedgerClient ledgerClient;

    /**
     * Owner-scoped endpoint: restaurant users access their own order earnings.
     * The outletId in the path is validated against the order's restaurantId
     * and ownership is checked via MoneyAccessPolicy.
     */
    @GetMapping("/{outletId}/orders/{orderId}")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).RESTAURANT, #outletId)")
    public ResponseEntity<RestaurantOrderEarnings> fetchOrderEarnings(
            @PathVariable("outletId") UUID outletId,
            @PathVariable("orderId") UUID orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Validate the outletId matches the order's restaurant
        if (!order.getRestaurantId().equals(outletId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order does not belong to this outlet");
        }



        return ResponseEntity.ok(buildRestaurantEarnings(order));
    }


    /**
     * Refunds still in flight for this outlet.
     *
     * <p>Moved here 2026-09-09 from {@code RestaurantSupportTicketController}, which mapped it under
     * {@code /api/v1/internal/restaurants/outlets/**}. The gateway 403s external calls to
     * {@code /api/v1/internal/**} outside the admin carve-out, so the restaurant screen calling it
     * could never have worked — and it was guarded by role alone, with no ownership check, so had
     * it been reachable any RESTAURANT user could have read any outlet's refunds. It now sits on
     * the routed, owner-scoped money surface beside its COMPLETED counterpart below.
     */
    @GetMapping("/{outletId}/refund-requests")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).RESTAURANT, #outletId)")
    public ResponseEntity<java.util.List<com.fooddelivery.order.refund.RefundView>> fetchActiveRefundRequests(
            @PathVariable UUID outletId) {

        java.util.List<com.fooddelivery.order.entity.Refund> refunds =
                refundRepository.findRestaurantFaultRefunds(outletId, com.fooddelivery.common.enums.RefundStatus.PROCESSING);

        return ResponseEntity.ok(refunds.stream()
                .map(r -> com.fooddelivery.order.refund.RefundView.builder()
                        .id(r.getId())
                        .orderId(r.getOrderId())
                        .amount(r.getAmount())
                        .status(r.getStatus())
                        .destination(r.getDestination())
                        // method is deliberately absent: it lives on the order, and fetching one per
                        // refund here would be an N+1. The restaurant view does not show it.
                        .reasonCode(r.getReasonCode())
                        .requestedAt(r.getCreatedAt())
                        .completedAt(r.getCompletedAt())
                        .build())
                .collect(java.util.stream.Collectors.toList()));
    }

    @GetMapping("/{outletId}/refunds")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).RESTAURANT, #outletId)")
    public ResponseEntity<java.util.List<com.fooddelivery.order.refund.RefundView>> fetchRestaurantRefunds(
            @PathVariable UUID outletId) {



        // Fetch all completed refunds that were RESTAURANT_FAULT
        java.util.List<com.fooddelivery.order.entity.Refund> refunds = refundRepository.findRestaurantFaultRefunds(outletId, com.fooddelivery.common.enums.RefundStatus.COMPLETED);
        
        java.util.List<com.fooddelivery.order.refund.RefundView> views = refunds.stream()
            .map(r -> com.fooddelivery.order.refund.RefundView.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .amount(r.getAmount())
                .status(r.getStatus())
                .destination(r.getDestination())
                .reasonCode(r.getReasonCode())
                .requestedAt(r.getCreatedAt())
                // completedAt, not updatedAt: the last touch on the row is not when the money
                // moved. Corrected here 2026-09-09 to match RestaurantSupportTicketController,
                // whose single endpoint was folded into this class.
                .completedAt(r.getCompletedAt())
                .build())
            .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(views);
    }

    @GetMapping("/{outletId}/summary")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).RESTAURANT, #outletId)")
    public ResponseEntity<com.fooddelivery.customer.dto.RestaurantSummary> fetchSummary(
            @PathVariable UUID outletId,
            @RequestParam(required = false, defaultValue = "month") String period) {
        

        
        return ResponseEntity.ok(restaurantSummaryService.getSummary(outletId, period));
    }

    @GetMapping("/{outletId}/statement")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).RESTAURANT, #outletId)")
    public ResponseEntity<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.common.dto.ledger.LedgerStatementLineDto>> fetchStatement(
            @PathVariable UUID outletId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        

        
        return ResponseEntity.ok(ledgerClient.getStatement("RESTAURANT_PAYABLE", outletId, page, size));
    }

    @GetMapping("/{outletId}/orders")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).RESTAURANT, #outletId)")
    public ResponseEntity<java.util.List<RestaurantOrderEarnings>> fetchOrders(
            @PathVariable UUID outletId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(value = "page", defaultValue = "0") int page) {
        
        java.time.LocalDateTime start = from != null ? java.time.LocalDateTime.parse(from, java.time.format.DateTimeFormatter.ISO_DATE_TIME) : java.time.LocalDateTime.now().minusMonths(1);
        java.time.LocalDateTime end = to != null ? java.time.LocalDateTime.parse(to, java.time.format.DateTimeFormatter.ISO_DATE_TIME) : java.time.LocalDateTime.now().plusDays(1);
        java.util.List<Order> orders = orderRepository.findByRestaurantId(outletId).stream()
                .filter(o -> o.getCreatedAt().isAfter(start) && o.getCreatedAt().isBefore(end))
                .collect(java.util.stream.Collectors.toList());
        
        java.util.List<RestaurantOrderEarnings> earnings = orders.stream()
            .map(this::buildRestaurantEarnings)
            .collect(java.util.stream.Collectors.toList());
            
        return ResponseEntity.ok(earnings);
    }

    private RestaurantOrderEarnings buildRestaurantEarnings(Order order) {
        RestaurantOrderEarnings earnings = new RestaurantOrderEarnings();
        earnings.setOrderId(order.getId());
        earnings.setRestaurantId(order.getRestaurantId());

        java.math.BigDecimal itemTotal = order.getOrderItems().stream()
            .map(i -> i.getPrice().multiply(java.math.BigDecimal.valueOf(i.getQuantity())))
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        earnings.setFoodCost(requireFinancial(itemTotal, "itemTotal"));
        earnings.setPlatformFee(requireFinancial(order.getRestaurantPlatformFee(), "restaurantPlatformFee"));
        earnings.setDeliveryContribution(requireFinancial(order.getRestaurantDeliveryContribution(), "restaurantDeliveryContribution"));
        earnings.setNetPayout(requireFinancial(order.getRestaurantPayout(), "restaurantPayout"));

        return earnings;
    }

    private java.math.BigDecimal requireFinancial(java.math.BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalStateException("Missing financial value for: " + field);
        }
        return value;
    }
}
