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

    /*
     * A note on the @PreAuthorize expressions below.
     *
     * These used `principal.name`. In a Spring Security SpEL expression `principal` is
     * `authentication.getPrincipal()`, NOT the `Principal` method parameter these handlers
     * declare -- and the principal object has no `name` property, so every expression threw
     * before the handler ran:
     *
     *   400 "Failed to evaluate expression '@moneyAccessPolicy.canAccessMoney(authentication,
     *        ...CUSTOMER, T(java.util.UUID).fromString(principal.name))'"
     *
     * All five customer money endpoints were dead because of it -- wallet, wallet transactions,
     * refunds, order refunds and receipt. Observed live on 2026-09-19 while paying by Wallet:
     * the order was created and then auto-cancelled.
     *
     * `authentication.name` is the correct form and is what the other 27 money expressions in
     * this workspace use, including DriverMoneyController, whose endpoints work.
     * MoneyAccessPolicyTest confirms getName() carries the owner UUID.
     */

    private final RefundRepository refundRepository;
    private final com.fooddelivery.order.repository.IOrderRepository orderRepository;
    private final com.fooddelivery.customer.service.money.CustomerReceiptService customerReceiptService;
    private final com.fooddelivery.customer.service.money.CustomerInvoiceService customerInvoiceService;
    private final com.fooddelivery.common.client.WalletServiceClient walletServiceClient;

    /**
     * The customer's own wallet.
     *
     * <p>Phase 1 deleted {@code /api/v1/wallets/**} and moved owner-scoped wallet reads behind
     * {@code MoneyAccessPolicy} in the owning services. This is the customer half of that; the
     * advertiser half is {@code /api/v1/money/advertiser} in WalletService, which the gateway routes
     * there directly. WalletService's own endpoints are SERVICE/ADMIN-only, so this call carries the
     * service identity and the ownership decision is made here, against the signed-in principal.
     *
     * <p>Until 2026-09-09 neither half existed and four screens called the deleted path through a
     * stale generated client, showing a zero balance whatever the wallet held.
     */
    @GetMapping("/wallet")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).CUSTOMER, T(java.util.UUID).fromString(authentication.name))")
    public ResponseEntity<com.fooddelivery.common.dto.wallet.WalletDto> getMyWallet(Principal principal) {
        return ResponseEntity.ok(walletServiceClient.getWallet(
                com.fooddelivery.common.enums.WalletEntityType.CUSTOMER.name(),
                UUID.fromString(principal.getName())));
    }

    @GetMapping("/wallet/transactions")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).CUSTOMER, T(java.util.UUID).fromString(authentication.name))")
    public ResponseEntity<com.fooddelivery.common.dto.PageResponseDto<java.util.Map<String, Object>>> getMyWalletTransactions(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(walletServiceClient.getWalletTransactions(
                com.fooddelivery.common.enums.WalletEntityType.CUSTOMER.name(),
                UUID.fromString(principal.getName()), page, size));
    }

    @GetMapping("/refunds")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).CUSTOMER, T(java.util.UUID).fromString(authentication.name))")
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
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).CUSTOMER, T(java.util.UUID).fromString(authentication.name))")
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
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).CUSTOMER, T(java.util.UUID).fromString(authentication.name))")
    public ResponseEntity<com.fooddelivery.customer.dto.CustomerReceipt> getReceipt(@PathVariable UUID orderId, Principal principal) {
        UUID customerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(customerReceiptService.getReceipt(orderId, customerId));
    }
    
    /** The GST tax invoice for a delivered order; issued on first request. 409 until delivered. */
    @GetMapping("/orders/{orderId}/invoice")
    @PreAuthorize("@moneyAccessPolicy.canAccessMoney(authentication, T(com.fooddelivery.common.security.money.MoneyOwnerType).CUSTOMER, T(java.util.UUID).fromString(authentication.name))")
    public ResponseEntity<com.fooddelivery.customer.dto.CustomerInvoice> getInvoice(@PathVariable UUID orderId, Principal principal) {
        UUID customerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(customerInvoiceService.getInvoice(orderId, customerId));
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
