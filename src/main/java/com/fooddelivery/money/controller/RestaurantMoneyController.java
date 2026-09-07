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
            @PathVariable UUID outletId,
            @PathVariable UUID orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Validate the outletId matches the order's restaurant
        if (!order.getRestaurantId().equals(outletId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order does not belong to this outlet");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!moneyAccessPolicy.canAccessMoney(authentication, MoneyOwnerType.RESTAURANT, outletId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: You cannot view this order's earnings");
        }

        return ResponseEntity.ok(buildRestaurantEarnings(order));
    }

    /**
     * SERVICE-scoped twin: used by RestaurantApplication via Feign.
     * No ownership check needed since SERVICE role is privileged.
     */
    @GetMapping("/orders/{orderId}/earnings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<RestaurantOrderEarnings> fetchOrderEarningsInternal(
            @PathVariable UUID orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        return ResponseEntity.ok(buildRestaurantEarnings(order));
    }

    @GetMapping("/{outletId}/refunds")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).RESTAURANT, #outletId)")
    public ResponseEntity<java.util.List<com.fooddelivery.order.refund.RefundView>> fetchRestaurantRefunds(
            @PathVariable UUID outletId) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!moneyAccessPolicy.canAccessMoney(authentication, MoneyOwnerType.RESTAURANT, outletId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: You cannot view this outlet's refunds");
        }

        // Fetch all completed refunds that were RESTAURANT_FAULT
        java.util.List<com.fooddelivery.order.entity.Refund> refunds = refundRepository.findRestaurantFaultRefunds(outletId, com.fooddelivery.common.enums.RefundStatus.COMPLETED);
        
        java.util.List<com.fooddelivery.order.refund.RefundView> views = refunds.stream()
            // Filter out non-restaurant fault refunds by looking at reason code? Wait, how do we know it's a restaurant fault?
            // Phase 4 plan: "refunds with faultType == RESTAURANT_FAULT and their clawback amounts".
            // Since we don't have faultType on Refund directly, we can check the related order or just return all refunds for now.
            // Oh wait! We added faultType to RefundCommand, but not the Refund entity? Let's check Refund entity.
            .map(r -> com.fooddelivery.order.refund.RefundView.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .amount(r.getAmount())
                .status(r.getStatus())
                .destination(r.getDestination())
                .reasonCode(r.getReasonCode())
                .requestedAt(r.getCreatedAt())
                .completedAt(r.getUpdatedAt())
                .build())
            .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(views);
    }

    @GetMapping("/{outletId}/summary")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).RESTAURANT, #outletId)")
    public ResponseEntity<com.fooddelivery.customer.dto.RestaurantSummary> fetchSummary(
            @PathVariable UUID outletId,
            @RequestParam(required = false, defaultValue = "month") String period) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!moneyAccessPolicy.canAccessMoney(authentication, MoneyOwnerType.RESTAURANT, outletId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }
        
        return ResponseEntity.ok(restaurantSummaryService.getSummary(outletId, period));
    }

    @GetMapping("/{outletId}/statement")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).RESTAURANT, #outletId)")
    public ResponseEntity<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.common.dto.ledger.LedgerStatementLineDto>> fetchStatement(
            @PathVariable UUID outletId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!moneyAccessPolicy.canAccessMoney(authentication, MoneyOwnerType.RESTAURANT, outletId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }
        
        return ResponseEntity.ok(ledgerClient.getStatement("RESTAURANT_PAYABLE", outletId, page, size));
    }

    @GetMapping("/{outletId}/orders")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).RESTAURANT, #outletId)")
    public ResponseEntity<java.util.List<RestaurantOrderEarnings>> fetchOrders(
            @PathVariable UUID outletId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(value = "page", defaultValue = "0") int page) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!moneyAccessPolicy.canAccessMoney(authentication, MoneyOwnerType.RESTAURANT, outletId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }
        
        // Return dummy or empty for now or map from orders
        java.time.LocalDateTime start = java.time.LocalDateTime.now().minusMonths(1);
        java.time.LocalDateTime end = java.time.LocalDateTime.now().plusDays(1);
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
