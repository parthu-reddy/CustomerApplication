package com.fooddelivery.order.lifecycle;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A settled order cannot be relabelled.
 *
 * <p>All four terminal statuses share sequence 100, so the backward-transition guard could not see
 * a move between two of them: a CANCELLED order could be turned into CANCELLED_BY_RESTAURANT by a
 * late event. Who ended the order decides who is clawed back for it, so that is a money question,
 * not bookkeeping.
 */
class TerminalStatusIsFinalTest {

    private Order orderIn(OrderStatus status) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setStatus(status);
        return o;
    }

    @Test
    void noTerminalStatusCanBecomeAnotherTerminalStatus() {
        OrderStatus[] terminals = java.util.Arrays.stream(OrderStatus.values())
                .filter(OrderStatus::isTerminal).toArray(OrderStatus[]::new);
        assertTrue(terminals.length >= 4, "expected four terminal statuses, found " + terminals.length);

        for (OrderStatus from : terminals) {
            for (OrderStatus to : terminals) {
                if (from == to) {
                    continue;
                }
                Order o = orderIn(from);
                IllegalStateException e = assertThrows(IllegalStateException.class,
                        () -> o.setStatus(to), from + " -> " + to + " must be refused");
                assertTrue(e.getMessage().contains("terminal"), e.getMessage());
                assertEquals(from, o.getStatus(), "the refused transition must not have taken effect");
            }
        }
    }

    @Test
    void settingTheSameTerminalStatusAgainIsIdempotent() {
        for (OrderStatus terminal : java.util.Arrays.stream(OrderStatus.values())
                .filter(OrderStatus::isTerminal).toList()) {
            Order o = orderIn(terminal);
            assertDoesNotThrow(() -> o.setStatus(terminal),
                    terminal + " set twice must not throw: redelivery is normal");
            assertEquals(terminal, o.getStatus());
        }
    }

    @Test
    void reachingATerminalStatusFromALiveOneStillWorks() {
        for (OrderStatus terminal : java.util.Arrays.stream(OrderStatus.values())
                .filter(OrderStatus::isTerminal).toList()) {
            Order o = orderIn(OrderStatus.HANDED_OVER);
            assertDoesNotThrow(() -> o.setStatus(terminal), "HANDED_OVER -> " + terminal);
            assertEquals(terminal, o.getStatus());
        }
    }

    @Test
    void movingBackwardIsStillRefused() {
        Order o = orderIn(OrderStatus.PREPARING);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> o.setStatus(OrderStatus.CREATED));
        assertTrue(e.getMessage().contains("backward"), e.getMessage());
    }
}
