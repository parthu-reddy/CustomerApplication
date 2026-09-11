package com.fooddelivery.order.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
import com.fooddelivery.order.service.state.impl.AcceptedState;
import com.fooddelivery.order.service.state.impl.HandedOverState;
import com.fooddelivery.order.service.state.impl.PreparingState;
import com.fooddelivery.order.service.state.impl.ReadyForPickupState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * A paid order the platform cannot deliver ends as the platform's failure, immediately.
 *
 * <p>`handleDispatchFailed` used to set a delivery status and stop. The order sat paid, with no
 * driver, in a restaurant-owned state until the 60-minute stuck sweeper cancelled it as
 * CANCELLED_BY_RESTAURANT with RESTAURANT_FAULT — which is what decides the clawback, so the
 * restaurant paid for the platform's failure to find a rider, an hour late.
 */
class DispatchFailureTerminationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Order order(OrderStatus status) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCustomerId(UUID.randomUUID());
        o.setRestaurantId(UUID.randomUUID());
        o.setTotalAmount(new BigDecimal("420.00"));
        o.setPaymentMethod(PaymentMethod.CARD);
        o.setPaymentStatus(PaymentIntentStatus.SUCCESS);
        o.setStatus(status);
        return o;
    }

    private OrderContext ctx(Order o) {
        return new OrderContext(o, objectMapper.createObjectNode(), mock(OrderActionService.class),
                mock(LedgerBookkeeper.class), null, o.getPaymentMethod());
    }

    /** Every state that handles DISPATCH_FAILED must terminate and refund. */
    @Test
    void everyStateThatHandlesDispatchFailureTerminatesAndRefunds() {
        Map<OrderState, OrderStatus> states = Map.of(
                new AcceptedState(), OrderStatus.ACCEPTED,
                new PreparingState(), OrderStatus.PREPARING,
                new ReadyForPickupState(), OrderStatus.READY_FOR_PICKUP);

        states.forEach((state, from) -> {
            Order o = order(from);
            OrderContext ctx = ctx(o);

            state.handleDispatchFailed(ctx);

            assertEquals(OrderStatus.CANCELLED_BY_PLATFORM, o.getStatus(),
                    "from " + from + ": a dispatch failure is the platform's, not the restaurant's");
            assertTrue(o.getStatus().isTerminal(), "from " + from + ": the order must be over");
            assertEquals(DeliveryStatus.FAILED, o.getDeliveryStatus());
            assertTrue(ctx.isRequiresRefund(), "from " + from + ": the customer paid and gets nothing");
            assertNotNull(o.getCancellationReason(), "from " + from + ": the customer is owed a reason");
        });
    }

    @Test
    void theReasonFromTheEventIsKeptWhenThereIsOne() {
        Order o = order(OrderStatus.ACCEPTED);
        com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
        payload.put("reason", "All riders busy in this zone");
        OrderContext ctx = new OrderContext(o, payload, mock(OrderActionService.class),
                mock(LedgerBookkeeper.class), null, o.getPaymentMethod());

        new AcceptedState().handleDispatchFailed(ctx);

        assertEquals("All riders busy in this zone", o.getCancellationReason());
    }

    /** DELIVERY_FAILED must terminate too, or the stuck sweeper cancels the order a second time. */
    @Test
    void deliveryFailureTerminatesFromEveryStateThatHandlesIt() {
        for (Object[] pair : new Object[][]{
                {new ReadyForPickupState(), OrderStatus.READY_FOR_PICKUP},
                {new HandedOverState(), OrderStatus.HANDED_OVER}}) {
            Order o = order((OrderStatus) pair[1]);
            OrderContext ctx = ctx(o);

            ((OrderState) pair[0]).handleDeliveryFailed(ctx);

            assertEquals(OrderStatus.DELIVERY_FAILED, o.getStatus(), "from " + pair[1]);
            assertTrue(o.getStatus().isTerminal());
            assertEquals(DeliveryStatus.FAILED, o.getDeliveryStatus());
            assertTrue(ctx.isRequiresRefund());
        }
    }

    /** Every terminal status has a state, so the factory cannot throw on one at runtime. */
    @Test
    void everyTerminalStatusHasAState() {
        for (OrderStatus status : OrderStatus.values()) {
            assertNotNull(com.fooddelivery.order.service.state.OrderStateFactory.getState(status),
                    status + " has no state");
        }
        assertEquals(List.of(OrderStatus.CANCELLED, OrderStatus.CANCELLED_BY_RESTAURANT,
                        OrderStatus.CANCELLED_BY_PLATFORM, OrderStatus.DELIVERY_FAILED),
                java.util.Arrays.stream(OrderStatus.values()).filter(OrderStatus::isTerminal).toList(),
                "the terminal set is what the rest of this phase is built on");
    }
}
