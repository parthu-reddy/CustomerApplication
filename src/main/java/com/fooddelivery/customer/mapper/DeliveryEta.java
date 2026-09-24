package com.fooddelivery.customer.mapper;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;

/**
 * When the food should reach the door, for the customer's tracker.
 *
 * <p>Before pickup: when the kitchen says it is ready ({@code estimatedCompletionTime}), or now if
 * that has passed, plus the road time restaurant to door. After pickup: the handover plus the same
 * road time -- never earlier than now, because a late rider is still coming.
 *
 * <p>It does not use the rider's live position (only the rider and services may read it), so it
 * is the route's normal driving time, not a live traffic-aware figure. Null whenever an input is
 * missing: no estimate is better than an invented one.
 */
public final class DeliveryEta {

    private DeliveryEta() {
    }

    public static Long arrivalEpochMs(Order order, long nowMs) {
        Integer travel = order.getDeliveryTravelSeconds();
        if (travel == null || travel < 0 || order.getStatus() == null) return null;
        // A delivered order stays HANDED_OVER; it has arrived, there is nothing to estimate.
        if (order.getDeliveredAt() != null) return null;
        long travelMs = travel * 1000L;
        OrderStatus status = order.getStatus();
        switch (status) {
            case ACCEPTED, PREPARING, READY_FOR_PICKUP -> {
                Long ready = order.getEstimatedCompletionTime();
                if (ready == null) return null;
                return Math.max(ready, nowMs) + travelMs;
            }
            case HANDED_OVER -> {
                Long pickedUp = order.getHandedOverAt();
                if (pickedUp == null) return null;
                return Math.max(pickedUp + travelMs, nowMs);
            }
            default -> {
                // Not yet accepted (no ready time to count from) or finished.
                return null;
            }
        }
    }
}
