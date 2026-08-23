package com.fooddelivery.order.security;

import com.fooddelivery.common.client.RestaurantServiceClient;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class OrderSecurityHelper {
    private static final String SERVICE_NAME = "customer-service";

    private final IOrderRepository orderRepository;
    private final RestaurantServiceClient restaurantServiceClient;

    /**
     * True when {@code userId} is the customer, the assigned driver, or the owner of the
     * outlet on this order.
     *
     * <p>Note the asymmetry: {@code customerId} and {@code deliveryExecutiveId} are user
     * ids and compare directly, but {@code restaurantId} is an OUTLET id. Comparing it to
     * a user id never matches, which locked restaurant owners out of their own orders.
     * Ownership has to be resolved through restaurant-service, which is the only place
     * that knows outlet -> brand -> owner.
     */
    public boolean isOrderParticipant(UUID orderId, String userId) {
        if (userId == null || orderId == null) {
            return false;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return false;
        }
        if (order.getCustomerId() != null && order.getCustomerId().toString().equals(userId)) {
            return true;
        }
        if (order.getDeliveryExecutiveId() != null && order.getDeliveryExecutiveId().toString().equals(userId)) {
            return true;
        }
        return isOutletOwner(order.getRestaurantId(), userId);
    }

    private boolean isOutletOwner(UUID outletId, String userId) {
        if (outletId == null) {
            return false;
        }
        try {
            java.util.List<String> owned = restaurantServiceClient.getOwnerOutlets(userId, SERVICE_NAME);
            return owned != null && owned.contains(outletId.toString());
        } catch (Exception e) {
            // Fail closed: an unavailable dependency must not grant access.
            log.warn("Could not resolve outlet ownership for user {} on outlet {}; denying", userId, outletId, e);
            return false;
        }
    }
}
