package com.fooddelivery.order.security;

import com.fooddelivery.common.security.money.MoneyAccessPolicy;
import com.fooddelivery.common.security.money.MoneyOwnerType;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class OrderSecurityHelper {

    private final IOrderRepository orderRepository;
    private final MoneyAccessPolicy moneyAccessPolicy;

    /**
     * True when {@code userId} is the customer, the assigned driver, or the owner of the
     * outlet on this order.
     *
     * <p>Note the asymmetry: {@code customerId} and {@code deliveryExecutiveId} are user
     * ids and compare directly, but {@code restaurantId} is an OUTLET id. Comparing it to
     * a user id never matches, which locked restaurant owners out of their own orders.
     * Ownership is now resolved through {@link MoneyAccessPolicy}, which delegates to
     * restaurant-service and caches the result.
     */
    /**
     * True only when {@code userId} is the customer who placed this order.
     *
     * <p>Deliberately narrower than {@link #isOrderParticipant}: that one also admits the assigned
     * driver and the outlet owner, and the review context this guards carries the customer's own
     * name. A driver must not be able to read it.
     */
    public boolean isOrderCustomer(UUID orderId, String userId) {
        if (userId == null || orderId == null) {
            return false;
        }
        return orderRepository.findById(orderId)
                .map(order -> order.getCustomerId() != null
                        && order.getCustomerId().toString().equals(userId))
                .orElse(false);
    }

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
        // Delegate outlet ownership to the centralized MoneyAccessPolicy (cached, fail-closed)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return moneyAccessPolicy.canAccessMoney(auth, MoneyOwnerType.RESTAURANT, order.getRestaurantId());
    }
}
