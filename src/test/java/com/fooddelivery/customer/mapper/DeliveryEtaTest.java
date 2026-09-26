package com.fooddelivery.customer.mapper;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeliveryEtaTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final int TRAVEL = 600; // 10 min

    private static Order order(OrderStatus status) {
        Order o = new Order();
        o.setStatus(status);
        o.setDeliveryTravelSeconds(TRAVEL);
        return o;
    }

    @Test
    void whileCooking_readyTimePlusTheDrive() {
        Order o = order(OrderStatus.PREPARING);
        o.setEstimatedCompletionTime(NOW + 15 * 60_000);
        assertEquals(NOW + 25 * 60_000, DeliveryEta.arrivalEpochMs(o, NOW));
    }

    @Test
    void aLateKitchenCountsFromNow_notFromAReadyTimeAlreadyPast() {
        Order o = order(OrderStatus.READY_FOR_PICKUP);
        o.setEstimatedCompletionTime(NOW - 5 * 60_000);
        assertEquals(NOW + 10 * 60_000, DeliveryEta.arrivalEpochMs(o, NOW));
    }

    @Test
    void afterPickup_handoverPlusTheDrive_neverInThePast() {
        Order o = order(OrderStatus.HANDED_OVER);
        o.setHandedOverAt(NOW - 4 * 60_000);
        assertEquals(NOW + 6 * 60_000, DeliveryEta.arrivalEpochMs(o, NOW));
        o.setHandedOverAt(NOW - 30 * 60_000);
        assertEquals(NOW, DeliveryEta.arrivalEpochMs(o, NOW));
    }

    @Test
    void noEstimateWithoutItsInputs_orOnceItIsOver() {
        assertNull(DeliveryEta.arrivalEpochMs(order(OrderStatus.PENDING_ACCEPTANCE), NOW));
        assertNull(DeliveryEta.arrivalEpochMs(order(OrderStatus.PREPARING), NOW)); // no ready time
        Order noRoute = order(OrderStatus.PREPARING);
        noRoute.setDeliveryTravelSeconds(null);
        noRoute.setEstimatedCompletionTime(NOW);
        assertNull(DeliveryEta.arrivalEpochMs(noRoute, NOW));
        Order delivered = order(OrderStatus.HANDED_OVER);
        delivered.setHandedOverAt(NOW);
        delivered.setDeliveredAt(Instant.now());
        assertNull(DeliveryEta.arrivalEpochMs(delivered, NOW));
        assertNull(DeliveryEta.arrivalEpochMs(order(OrderStatus.CANCELLED), NOW));
    }
}
