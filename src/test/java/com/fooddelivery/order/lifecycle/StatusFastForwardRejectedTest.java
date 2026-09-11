package com.fooddelivery.order.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.exception.IllegalStateTransitionException;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
import com.fooddelivery.order.service.state.OrderStateFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * There is no generic "set the status to whatever the payload says".
 *
 * <p>The default handler used to fast-forward to any status with a higher sequence, taken straight
 * from an ORDER_STATUS_UPDATED payload — so an event could move an order to HANDED_OVER without it
 * ever having been accepted, prepared or made ready, and the restaurant would be paid for food it
 * never cooked.
 */
class StatusFastForwardRejectedTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrderContext ctx(OrderStatus status, String payloadStatus) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCustomerId(UUID.randomUUID());
        o.setTotalAmount(new BigDecimal("420.00"));
        o.setPaymentMethod(PaymentMethod.CARD);
        o.setStatus(status);
        ObjectNode payload = objectMapper.createObjectNode();
        if (payloadStatus != null) {
            payload.put("status", payloadStatus);
        }
        return new OrderContext(o, payload, mock(OrderActionService.class),
                mock(LedgerBookkeeper.class), null, o.getPaymentMethod());
    }

    @Test
    void anEventCannotJumpAnOrderToHandedOver() {
        OrderContext c = ctx(OrderStatus.PENDING_ACCEPTANCE, OrderStatus.HANDED_OVER.name());

        assertThrows(IllegalStateTransitionException.class,
                () -> OrderStateFactory.getState(OrderStatus.PENDING_ACCEPTANCE).handleStatusUpdate(c));
        assertEquals(OrderStatus.PENDING_ACCEPTANCE, c.getOrder().getStatus());
    }

    @Test
    void noLiveStateAcceptsAnArbitraryForwardJump() {
        for (OrderStatus from : OrderStatus.values()) {
            if (from.isTerminal()) {
                continue; // terminal states ignore with a warning, asserted separately
            }
            OrderContext c = ctx(from, OrderStatus.HANDED_OVER.name());
            OrderState state = OrderStateFactory.getState(from);
            if (from == OrderStatus.READY_FOR_PICKUP) {
                continue; // its one legitimate promotion is asserted below
            }
            assertThrows(IllegalStateTransitionException.class, () -> state.handleStatusUpdate(c),
                    from + " accepted an arbitrary status update");
        }
    }

    /** The one real transition this handler exists for. */
    @Test
    void readyForPickupStillPromotesOutForDelivery() {
        OrderContext c = ctx(OrderStatus.READY_FOR_PICKUP, DeliveryStatus.OUT_FOR_DELIVERY.name());

        OrderStateFactory.getState(OrderStatus.READY_FOR_PICKUP).handleStatusUpdate(c);

        assertEquals(OrderStatus.HANDED_OVER, c.getOrder().getStatus());
        assertEquals(DeliveryStatus.OUT_FOR_DELIVERY, c.getOrder().getDeliveryStatus());
    }

    @Test
    void readyForPickupRefusesAnyOtherStatusUpdate() {
        OrderContext c = ctx(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED.name());

        assertThrows(IllegalStateTransitionException.class,
                () -> OrderStateFactory.getState(OrderStatus.READY_FOR_PICKUP).handleStatusUpdate(c));
    }

    @Test
    void aTerminalOrderIgnoresStatusUpdatesRatherThanThrowing() {
        for (OrderStatus terminal : java.util.Arrays.stream(OrderStatus.values())
                .filter(OrderStatus::isTerminal).toList()) {
            OrderContext c = ctx(terminal, OrderStatus.HANDED_OVER.name());
            assertDoesNotThrow(() -> OrderStateFactory.getState(terminal).handleStatusUpdate(c),
                    terminal + ": a late event on a settled order is noise, not an error");
            assertEquals(terminal, c.getOrder().getStatus());
        }
    }

    @Test
    void aPayloadWithNoStatusAtAllIsRefused() {
        OrderContext c = ctx(OrderStatus.ACCEPTED, null);

        assertThrows(IllegalStateTransitionException.class,
                () -> OrderStateFactory.getState(OrderStatus.ACCEPTED).handleStatusUpdate(c));
    }
}
