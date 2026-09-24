package com.fooddelivery.order.service.state;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.event.OrderDelayApprovalRequestedEvent;
import com.fooddelivery.customer.dto.OrderResponse;
import com.fooddelivery.customer.mapper.OrderMapper;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.impl.PendingAcceptanceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The restaurant's delay request reaches the customer with its length and reason.
 *
 * OrderDelayApprovalRequestedEvent always carried both; the order dropped them, so the customer
 * was asked to approve a delay without being told how long it was, and the UI filled the gap by
 * sending a hardcoded 15 the server never read.
 */
@ExtendWith(MockitoExtension.class)
class DelayRequestStateTest {

    @Mock
    private OrderActionService actionService;

    @Mock
    private com.fooddelivery.order.ledger.LedgerBookkeeper ledgerBookkeeper;

    private Order pendingOrder() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("150.00"));
        order.setSgst(BigDecimal.ZERO);
        order.setCgst(BigDecimal.ZERO);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setCustomerPlatformFee(BigDecimal.ZERO);
        order.setStatus(OrderStatus.PENDING_ACCEPTANCE);
        return order;
    }

    private OrderContext ctx(Order order, com.fooddelivery.common.event.OrderScopedEvent payload) {
        return new OrderContext(order, payload, actionService, ledgerBookkeeper, "UNKNOWN", order.getPaymentMethod());
    }

    @Test
    void storesTheRequestedMinutesAndReason_andExposesThemOnTheResponse() {
        Order order = pendingOrder();
        OrderDelayApprovalRequestedEvent event = OrderDelayApprovalRequestedEvent.builder()
                .orderId(order.getId().toString())
                .additionalPrepTimeMinutes(20)
                .delayReason("Tandoor is backed up")
                .build();

        new PendingAcceptanceState().handleDelayApprovalRequested(ctx(order, event));

        assertEquals(OrderStatus.AWAITING_DELAY_APPROVAL, order.getStatus());
        assertEquals(20, order.getRequestedDelayMinutes());
        assertEquals("Tandoor is backed up", order.getDelayReason());

        OrderResponse response = OrderMapper.mapToResponse(order);
        assertEquals(20, response.getRequestedDelayMinutes());
        assertEquals("Tandoor is backed up", response.getDelayReason());
    }

    @Test
    void aBlankReasonIsStoredAsNoReason() {
        Order order = pendingOrder();
        OrderDelayApprovalRequestedEvent event = OrderDelayApprovalRequestedEvent.builder()
                .orderId(order.getId().toString())
                .additionalPrepTimeMinutes(15)
                .delayReason("   ")
                .build();

        new PendingAcceptanceState().handleDelayApprovalRequested(ctx(order, event));

        assertEquals(15, order.getRequestedDelayMinutes());
        assertNull(order.getDelayReason());
    }

    @Test
    void anOverlongReasonIsCutToTheColumnWidth() {
        Order order = pendingOrder();
        OrderDelayApprovalRequestedEvent event = OrderDelayApprovalRequestedEvent.builder()
                .orderId(order.getId().toString())
                .additionalPrepTimeMinutes(30)
                .delayReason("x".repeat(600))
                .build();

        new PendingAcceptanceState().handleDelayApprovalRequested(ctx(order, event));

        assertEquals(500, order.getDelayReason().length());
    }
}
