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
import java.util.List;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasRole(\'CUSTOMER\')")
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class OrderController {
    

    private final CustomerOrderService customerOrderService;
    private final com.fooddelivery.common.service.RateLimitingService rateLimitingService;

    private boolean isRateLimited(String clientKey) {
        if (clientKey == null || clientKey.isBlank() || clientKey.equals("unknown")) return true;
        io.github.bucket4j.Bucket bucket = rateLimitingService.resolveBucket("order:" + clientKey, 100, 100, java.time.Duration.ofSeconds(10));
        return !bucket.tryConsume(1);
    }

    @PostMapping("/quote")
    public ResponseEntity<ApiResponse<com.fooddelivery.customer.dto.QuoteResponse>> quoteOrder(java.security.Principal principal, @Valid @RequestBody com.fooddelivery.customer.dto.QuoteRequest request) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        if (isRateLimited(customerId.toString())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).build();
        }
        try {
            com.fooddelivery.customer.dto.QuoteResponse response = customerOrderService.calculateOrderQuote(customerId, request).join();
            return ResponseEntity.ok(ApiResponse.success(response, "Quote generated successfully."));
        } catch (java.util.concurrent.CompletionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Failed to generate quote", cause);
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(java.security.Principal principal, @Valid @RequestBody OrderRequest request) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        if (isRateLimited(customerId.toString())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).build();
        }
        request.setCustomerId(customerId); // ensure customerId is set from principal
        try {
            var result = customerOrderService.createOrderWithPayment(request).join();
            OrderResponse response = com.fooddelivery.customer.mapper.OrderMapper.mapToResponse(result.order());
            response.setPaymentIntent(result.paymentIntent());
            return ResponseEntity.ok(ApiResponse.success(response, "Order created successfully. Please complete payment."));
        } catch (java.util.concurrent.CompletionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Failed to create order", cause);
        }
    }

    @PostMapping("/{orderId}/delay-approval")
    public ResponseEntity<ApiResponse<Void>> handleDelayApproval(java.security.Principal principal, @org.springframework.web.bind.annotation.PathVariable java.util.UUID orderId, @Valid @RequestBody com.fooddelivery.customer.dto.DelayApprovalRequest request) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        customerOrderService.handleDelayApproval(customerId, orderId, request.isApproved());
        return ResponseEntity.ok(ApiResponse.success(null, "Delay approval processed"));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(java.security.Principal principal, @org.springframework.web.bind.annotation.PathVariable java.util.UUID orderId) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        customerOrderService.cancelOrder(customerId, orderId);
        return ResponseEntity.ok(ApiResponse.success(null, "Order cancelled successfully"));
    }

    @org.springframework.web.bind.annotation.GetMapping("/active")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<OrderResponse>>> getActiveOrders(java.security.Principal principal, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<Order> orders = customerOrderService.getActiveOrdersPaginated(customerId, pageable);
        org.springframework.data.domain.Page<OrderResponse> responses = orders.map(com.fooddelivery.customer.mapper.OrderMapper::mapToResponse);
        return ResponseEntity.ok(ApiResponse.success(responses, "Active orders retrieved"));
    }

    @org.springframework.web.bind.annotation.GetMapping("/refunds")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<OrderResponse>>> getRefundOrders(java.security.Principal principal, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<Order> orders = customerOrderService.getRefundOrdersPaginated(customerId, pageable);
        org.springframework.data.domain.Page<OrderResponse> responses = orders.map(com.fooddelivery.customer.mapper.OrderMapper::mapToResponse);
        return ResponseEntity.ok(ApiResponse.success(responses, "Refund orders retrieved"));
    }

    @org.springframework.web.bind.annotation.GetMapping("/history")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<OrderResponse>>> getOrderHistory(java.security.Principal principal, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<Order> orders = customerOrderService.getOrderHistoryPaginated(customerId, pageable);
        org.springframework.data.domain.Page<OrderResponse> responses = orders.map(com.fooddelivery.customer.mapper.OrderMapper::mapToResponse);
        return ResponseEntity.ok(ApiResponse.success(responses, "Order history retrieved"));
    }

    @org.springframework.web.bind.annotation.GetMapping("/batch")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersBatch(java.security.Principal principal, @org.springframework.web.bind.annotation.RequestParam List<java.util.UUID> ids) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        List<Order> orders = customerOrderService.getOrdersByIdsAndCustomer(ids, customerId);
        List<OrderResponse> responses = orders.stream().map(com.fooddelivery.customer.mapper.OrderMapper::mapToResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(responses, "Batch orders retrieved"));
    }

    @org.springframework.web.bind.annotation.GetMapping("/{orderId}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(java.security.Principal principal, @org.springframework.web.bind.annotation.PathVariable java.util.UUID orderId) {
        java.util.UUID customerId = java.util.UUID.fromString(principal.getName());
        Order order = customerOrderService.getOrderByIdAndCustomer(orderId, customerId);
        OrderResponse response = com.fooddelivery.customer.mapper.OrderMapper.mapToResponse(order);
        response.setRestaurantName(null); // Intentionally break schema for testing Zod strictness
        return ResponseEntity.ok(ApiResponse.success(response, "Order retrieved"));
    }

    
}
