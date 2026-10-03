package com.fooddelivery.order.security;

import com.fooddelivery.common.client.RestaurantServiceClient;
import com.fooddelivery.common.security.organisation.OrganisationAccessPolicy;
import com.fooddelivery.common.enums.OrganisationPermission;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class OrderSecurityHelper {

    private final IOrderRepository orderRepository;
    private final RestaurantServiceClient restaurantServiceClient;
    private final OrganisationAccessPolicy organisationAccessPolicy;

    /**
     * True only when {@code userId} is the customer who placed this order.
     *
     * <p>Deliberately narrower than {@link #isOrderParticipant}: that one also admits the assigned
     * driver and authorised outlet staff, and the review context this guards carries the customer's own
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
        return isOutletOperator(order.getRestaurantId(), userId);
    }

    /** Operational refund queues and order participation use the same named membership. */
    public boolean isOutletOperator(UUID outletId, String userId) {
        if (outletId == null || userId == null) { return false; }
        // Check the named user, never a SERVICE/admin shortcut or an earnings permission.
        try {
            UUID actor = UUID.fromString(userId);
            var outlet = restaurantServiceClient.getOutletOrganisation(outletId);
            return outlet != null && outletId.equals(outlet.outletId()) && outlet.organisationId() != null
                    && organisationAccessPolicy.canUser(actor, outlet.organisationId(), OrganisationPermission.ORDERS_OPERATE);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
