package com.fooddelivery.customer.controller;

import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.order.enums.InitiatorType;
import com.fooddelivery.order.enums.RefundSource;
import com.fooddelivery.order.refund.RefundCommand;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.refund.RefundView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/admin/refunds")
@lombok.RequiredArgsConstructor
public class AdminRefundCommandController {

    private final RefundService refundService;

    @PostMapping("/request")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RefundView> requestRefund(
            @Valid @RequestBody AdminRefundRequest request,
            Authentication authentication) {
        UUID adminId = authenticatedAdminId(authentication);
        List<RefundCommand.Item> items = request.items().stream()
                .map(item -> new RefundCommand.Item(item.orderItemId(), item.quantity()))
                .toList();

        // A direct admin refund is always item-scoped. The server calculates the amount from the
        // order and never trusts a browser-supplied money value.
        BigDecimal quotedAmount = refundService.quote(request.orderId(), items);
        String reasonText = request.reasonText().trim();
        RefundView view = refundService.request(command(adminId, request, items, quotedAmount, reasonText));
        return ResponseEntity.ok(view);
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

    /**
     * The external administrative request deliberately exposes only business inputs. Actor,
     * source, destination, amount, and idempotency identity are server-owned so an admin browser
     * cannot forge a customer/system refund or choose a payment destination.
     */
    public record AdminRefundRequest(
            @NotNull UUID orderId,
            @NotEmpty @Size(max = 100) List<@Valid RefundItemRequest> items,
            @NotNull FaultType faultType,
            @NotBlank @Size(max = 2000) String reasonText) {
    }

    public record RefundItemRequest(
            @NotNull UUID orderItemId,
            @Positive int quantity) {
    }

    private RefundCommand command(UUID adminId, AdminRefundRequest request,
                                  List<RefundCommand.Item> items, BigDecimal quotedAmount,
                                  String reasonText) {
        return RefundCommand.builder()
                .orderId(request.orderId())
                .amount(quotedAmount)
                .items(items)
                .faultType(request.faultType())
                .source(RefundSource.ADMIN)
                .initiatorType(InitiatorType.ADMIN)
                .initiatorId(adminId)
                .reasonCode("ADMIN_DIRECT")
                .reasonText(reasonText)
                .idempotencyKey(serverDerivedIdempotencyKey(adminId, request, items, quotedAmount, reasonText))
                .build();
    }

    private String serverDerivedIdempotencyKey(UUID adminId, AdminRefundRequest request,
                                               List<RefundCommand.Item> items, BigDecimal quotedAmount,
                                               String reasonText) {
        String canonicalItems = items.stream()
                .sorted(Comparator.comparing(RefundCommand.Item::getOrderItemId))
                .map(item -> item.getOrderItemId() + ":" + item.getQuantity())
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
        String payload = adminId + "|" + request.orderId() + "|" + request.faultType().name()
                + "|" + quotedAmount.toPlainString() + "|" + reasonText + "|" + canonicalItems;
        return "admin_direct_refund:" + adminId + ":" + sha256(payload);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
