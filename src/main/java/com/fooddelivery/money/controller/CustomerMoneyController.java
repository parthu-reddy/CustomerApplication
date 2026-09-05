package com.fooddelivery.money.controller;

import com.fooddelivery.order.refund.RefundView;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.repository.RefundRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/money/customer")
@PreAuthorize("hasRole('CUSTOMER')")
@lombok.RequiredArgsConstructor
public class CustomerMoneyController {

    private final RefundRepository refundRepository;
    private final com.fooddelivery.order.repository.IOrderRepository orderRepository;
    private final com.fooddelivery.customer.service.money.CustomerReceiptService customerReceiptService;

    @GetMapping("/refunds")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).CUSTOMER, T(java.util.UUID).fromString(principal.name))")
    public ResponseEntity<List<RefundView>> getMyRefunds(Principal principal) {
        UUID customerId = UUID.fromString(principal.getName());
        
        // Find all orders for this customer to fetch their refunds
        List<com.fooddelivery.order.entity.Order> orders = orderRepository.findByCustomerId(customerId);
        List<UUID> orderIds = orders.stream().map(com.fooddelivery.order.entity.Order::getId).collect(Collectors.toList());
        
        // In a real app we'd have a refundRepository.findByOrderIdIn(orderIds)
        // Let's implement it or use a stream if missing
        List<RefundView> views = orders.stream()
            .flatMap(order -> refundRepository.findByOrderId(order.getId()).stream())
            .map(this::mapToView)
            .collect(Collectors.toList());
            
        return ResponseEntity.ok(views);
    }

    @GetMapping("/orders/{orderId}/refunds")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).CUSTOMER, T(java.util.UUID).fromString(principal.name))")
    public ResponseEntity<List<RefundView>> getOrderRefunds(@PathVariable UUID orderId, Principal principal) {
        UUID customerId = UUID.fromString(principal.getName());
        
        // Verify ownership
        com.fooddelivery.order.entity.Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found"));
            
        if (!order.getCustomerId().equals(customerId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Access Denied");
        }
        
        List<RefundView> views = refundRepository.findByOrderId(orderId).stream()
            .map(this::mapToView)
            .collect(Collectors.toList());
            
        return ResponseEntity.ok(views);
    }
    
    @GetMapping("/orders/{orderId}/receipt")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).CUSTOMER, T(java.util.UUID).fromString(principal.name))")
    public ResponseEntity<com.fooddelivery.customer.dto.CustomerReceipt> getReceipt(@PathVariable UUID orderId, Principal principal) {
        UUID customerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(customerReceiptService.getReceipt(orderId, customerId));
    }
    
    private RefundView mapToView(Refund r) {
        return RefundView.builder()
            .id(r.getId())
            .orderId(r.getOrderId())
            .amount(r.getAmount())
            .status(r.getStatus())
            .destination(r.getDestination())
            .reasonCode(r.getReasonCode())
            .requestedAt(r.getCreatedAt())
            .completedAt(r.getUpdatedAt())
            .build();
    }
}
