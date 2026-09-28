package com.fooddelivery.order.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fooddelivery.common.client.RestaurantServiceClient;
import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.common.dto.order.OrderReviewAuthorizationRequest;
import com.fooddelivery.common.dto.order.OrderReviewAuthorizationResult;
import com.fooddelivery.common.dto.order.OrderReviewTargetAuthorizationRequest;
import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.common.enums.ReviewEntityType;
import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Order-owned authorization for exact review targets. */
@RestController
@RequestMapping("/api/v1/internal/orders")
@RequiredArgsConstructor
public class InternalOrderReviewController {

    private static final String CALLING_SERVICE = "customer-service";

    private final IOrderRepository orderRepository;
    private final RestaurantServiceClient restaurantServiceClient;

    /**
     * Verifies each exact target against the immutable order snapshot and its participant graph.
     * Restaurant ownership is checked by RestaurantApplication, which owns that relationship.
     */
    @PostMapping("/{orderId}/review-authorizations")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<ApiResponse<List<OrderReviewAuthorizationResult>>> authorizeTargets(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderReviewAuthorizationRequest request) {
        // The repository loads orderItems in one short, read-only transaction. Restaurant ownership
        // is checked only after that transaction has closed, so a downstream call cannot pin a DB
        // connection while RestaurantApplication responds.
        Order order = orderRepository.findForReviewAuthorization(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("Order " + orderId + " was not found.", "ORDER_NOT_FOUND"));
        }

        boolean participant = isOrderParticipant(order, request);

        List<OrderReviewAuthorizationResult> results = request.getTargets().stream()
                .map(target -> authorizeTarget(order, request, target, participant))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(results, "Review targets authorized"));
    }

    private boolean isOrderParticipant(Order order, OrderReviewAuthorizationRequest request) {
        return switch (request.getReviewerRole()) {
            case CUSTOMER -> request.getReviewerId().equals(order.getCustomerId());
            case DELIVERY -> request.getReviewerId().equals(order.getDeliveryExecutiveId());
            case RESTAURANT -> ownsOrderOutlet(order.getRestaurantId(), request.getReviewerId());
            case ADMIN -> false;
        };
    }

    private boolean ownsOrderOutlet(UUID outletId, UUID ownerId) {
        if (outletId == null || ownerId == null) {
            return false;
        }
        return restaurantServiceClient.getOwnerOutlets(ownerId.toString(), CALLING_SERVICE)
                .stream()
                .anyMatch(outletId.toString()::equalsIgnoreCase);
    }

    private OrderReviewAuthorizationResult authorizeTarget(
            Order order,
            OrderReviewAuthorizationRequest request,
            OrderReviewTargetAuthorizationRequest target,
            boolean participant) {
        String reason = null;

        if (!participant) {
            reason = "ACTOR_NOT_PARTICIPANT";
        } else if (order.getDeliveryStatus() != DeliveryStatus.DELIVERED) {
            reason = "ORDER_NOT_DELIVERED";
        } else if ((target.getTargetType() == ReviewEntityType.CUSTOMER
                || target.getTargetType() == ReviewEntityType.DRIVER)
                && target.getTargetId().equalsIgnoreCase(request.getReviewerId().toString())) {
            reason = "SELF_REVIEW";
        } else if (!isAllowedTargetType(request.getReviewerRole(), target.getTargetType())) {
            reason = "ROLE_TARGET_NOT_ALLOWED";
        } else if (!isTargetOnOrder(order, target)) {
            reason = "TARGET_NOT_ON_ORDER";
        }

        return OrderReviewAuthorizationResult.builder()
                .targetType(target.getTargetType())
                .targetId(target.getTargetId())
                .allowed(reason == null)
                .reasonCode(reason)
                .build();
    }

    private static boolean isAllowedTargetType(RoleName role, ReviewEntityType type) {
        return switch (role) {
            case CUSTOMER -> type == ReviewEntityType.RESTAURANT
                    || type == ReviewEntityType.DRIVER || type == ReviewEntityType.PRODUCT;
            case RESTAURANT -> type == ReviewEntityType.CUSTOMER || type == ReviewEntityType.DRIVER;
            case DELIVERY -> type == ReviewEntityType.CUSTOMER || type == ReviewEntityType.RESTAURANT;
            case ADMIN -> false;
        };
    }

    private static boolean isTargetOnOrder(Order order, OrderReviewTargetAuthorizationRequest target) {
        return switch (target.getTargetType()) {
            case RESTAURANT -> order.getRestaurantId() != null
                    && order.getRestaurantId().toString().equalsIgnoreCase(target.getTargetId());
            case DRIVER -> order.getDeliveryExecutiveId() != null
                    && order.getDeliveryExecutiveId().toString().equalsIgnoreCase(target.getTargetId());
            case CUSTOMER -> order.getCustomerId() != null
                    && order.getCustomerId().toString().equalsIgnoreCase(target.getTargetId());
            case PRODUCT -> order.getOrderItems() != null && order.getOrderItems().stream()
                    .anyMatch(item -> item.getMenuItemId() != null
                            && item.getMenuItemId().toString().equalsIgnoreCase(target.getTargetId()));
        };
    }
}
