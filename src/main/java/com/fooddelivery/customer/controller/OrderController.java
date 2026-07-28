package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.dto.OrderRequest;
import com.fooddelivery.customer.dto.OrderResponse;
import com.fooddelivery.customer.dto.OrderItemResponse;
import com.fooddelivery.customer.service.CustomerOrderService;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;
import java.math.BigDecimal;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class OrderController {

    private final CustomerOrderService customerOrderService;

    @PostMapping
    public java.util.concurrent.CompletableFuture<ResponseEntity<ApiResponse<OrderResponse>>> createOrder(
            java.security.Principal principal,
            @Valid @RequestBody OrderRequest request) {
            
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        
        return customerOrderService.createOrderWithPayment(
                customerId,
                request.getRestaurantId(),
                request.getDeliveryAddressId(),
                request.getItems()
        ).thenApply(result -> {
            OrderResponse response = mapToResponse(result.order());
            response.setPaymentIntent(result.paymentIntent());
            return ResponseEntity.ok(ApiResponse.success(response, "Order created successfully"));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof com.fooddelivery.customer.exception.DeliveryPartnerUnavailableException || 
                cause instanceof com.fooddelivery.customer.exception.MenuItemsUnavailableException || 
                cause instanceof IllegalArgumentException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Failed to create order", cause);
        });
    }

    @PostMapping("/{orderId}/delay-approval")
    public ResponseEntity<ApiResponse<Void>> handleDelayApproval(
            java.security.Principal principal,
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID orderId,
            @Valid @RequestBody com.fooddelivery.customer.dto.DelayApprovalRequest request) {
        
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        
        customerOrderService.handleDelayApproval(customerId, orderId, request.isApproved());
        return ResponseEntity.ok(ApiResponse.success(null, "Delay approval processed"));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            java.security.Principal principal,
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID orderId) {
        
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        
        customerOrderService.cancelOrder(customerId, orderId);
        return ResponseEntity.ok(ApiResponse.success(null, "Order cancelled successfully"));
    }

    @org.springframework.web.bind.annotation.GetMapping("/active")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<OrderResponse>>> getActiveOrders(
            java.security.Principal principal,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<Order> orders = customerOrderService.getActiveOrdersPaginated(customerId, pageable);
        org.springframework.data.domain.Page<OrderResponse> responses = orders.map(this::mapToResponse);
        return ResponseEntity.ok(ApiResponse.success(responses, "Active orders retrieved"));
    }

    @org.springframework.web.bind.annotation.GetMapping("/refunds")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<OrderResponse>>> getRefundOrders(
            java.security.Principal principal,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<Order> orders = customerOrderService.getRefundOrdersPaginated(customerId, pageable);
        org.springframework.data.domain.Page<OrderResponse> responses = orders.map(this::mapToResponse);
        return ResponseEntity.ok(ApiResponse.success(responses, "Refund orders retrieved"));
    }

    @org.springframework.web.bind.annotation.GetMapping("/history")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<OrderResponse>>> getOrderHistory(
            java.security.Principal principal,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<Order> orders = customerOrderService.getOrderHistoryPaginated(customerId, pageable);
        org.springframework.data.domain.Page<OrderResponse> responses = orders.map(this::mapToResponse);
        return ResponseEntity.ok(ApiResponse.success(responses, "Order history retrieved"));
    }

    @org.springframework.web.bind.annotation.GetMapping("/{orderId}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            java.security.Principal principal,
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID orderId) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        Order order = customerOrderService.getOrderByIdAndCustomer(orderId, customerId);
        return ResponseEntity.ok(ApiResponse.success(mapToResponse(order), "Order retrieved"));
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .menuItemId(item.getMenuItemId())
                        .name(item.getName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .collect(Collectors.toList());

        BigDecimal itemTotal = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal deliveryFee = BigDecimal.ZERO;

        if (order.getCharges() != null) {
            for (com.fooddelivery.order.entity.OrderCharge charge : order.getCharges()) {
                if (com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER.equals(charge.getPayerType())) {
                    if (com.fooddelivery.common.enums.ChargeCategory.FOOD_COST.name().equals(charge.getCategory().name())) {
                        itemTotal = itemTotal.add(charge.getAmount());
                    } else if (com.fooddelivery.common.enums.ChargeCategory.SGST.name().equals(charge.getCategory().name())) {
                        sgst = sgst.add(charge.getAmount());
                    } else if (com.fooddelivery.common.enums.ChargeCategory.CGST.name().equals(charge.getCategory().name())) {
                        cgst = cgst.add(charge.getAmount());
                    } else if (com.fooddelivery.common.enums.ChargeCategory.DELIVERY_FEE.name().equals(charge.getCategory().name()) ||
                               com.fooddelivery.common.enums.ChargeCategory.PLATFORM_FIXED_FEE.name().equals(charge.getCategory().name())) {
                        deliveryFee = deliveryFee.add(charge.getAmount());
                    }
                }
            }
        }
        
        // If items exist, use item array to sum up food cost as a fallback or primary
        BigDecimal calculatedItemTotal = itemResponses.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (itemTotal.compareTo(BigDecimal.ZERO) == 0) {
            itemTotal = calculatedItemTotal;
        }

        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .restaurantId(order.getRestaurantId())
                .restaurantName(order.getRestaurantName())
                .status(order.getStatus())
                .deliveryStatus(order.getDeliveryStatus())

                .totalAmount(order.getTotalAmount())
                .itemTotal(itemTotal)
                .sgst(sgst)
                .cgst(cgst)
                .deliveryFee(deliveryFee)
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryLat(order.getDeliveryLat())
                .deliveryLng(order.getDeliveryLng())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .otp(order.getOtp())
                .pickupOtp(order.getPickupOtp())
                .estimatedCompletionTime(order.getEstimatedCompletionTime())
                .build();
    }
}
