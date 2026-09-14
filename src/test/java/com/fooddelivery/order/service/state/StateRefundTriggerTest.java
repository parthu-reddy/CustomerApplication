package com.fooddelivery.order.service.state;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.impl.PendingAcceptanceState;
import com.fooddelivery.order.service.state.impl.PreparingState;
import com.fooddelivery.order.service.state.impl.ReadyForPickupState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StateRefundTriggerTest {

    @Mock
    private OrderActionService actionService;

    @Mock
    private com.fooddelivery.order.ledger.LedgerBookkeeper ledgerBookkeeper;

    @Test
    void pendingAcceptanceState_handleCancel_SetsRequiresRefund() {
        PendingAcceptanceState state = new PendingAcceptanceState();
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("150.00"));
        order.setStatus(OrderStatus.PENDING_ACCEPTANCE);

        OrderContext ctx = new OrderContext(order, null, actionService, ledgerBookkeeper, "UNKNOWN", order.getPaymentMethod());

        state.cancelByCustomer(ctx, "Customer requested cancellation");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(ctx.isRequiresRefund());
        
        verify(actionService).emitOrderCancelledByCustomerEvent(eq(order.getId()));
    }

    @Test
    void preparingState_handleCancel_SetsRequiresRefund() {
        PreparingState state = new PreparingState();
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("200.00"));
        order.setStatus(OrderStatus.PREPARING);

        OrderContext ctx = new OrderContext(order, com.fooddelivery.common.event.OrderCancelledByRestaurantEvent.builder()
                .orderId(order.getId().toString()).reason("kitchen closed").build(), actionService, ledgerBookkeeper, "UNKNOWN", order.getPaymentMethod());

        state.handleOrderCancelledByRestaurant(ctx);

        assertEquals(OrderStatus.CANCELLED_BY_RESTAURANT, order.getStatus());
        // The context now carries the real event, so the reason on it must reach the order
        // rather than falling through to the generic default.
        assertEquals("kitchen closed", order.getCancellationReason());
        assertTrue(ctx.isRequiresRefund());
        
        verify(actionService).sendNotification(eq(order.getId().toString()), any(), eq(com.fooddelivery.common.constants.NotificationTemplate.ORDER_CANCELLED_BY_RESTAURANT));
    }
    
    @Test
    void readyForPickupState_handleCancel_SetsRequiresRefund() {
        ReadyForPickupState state = new ReadyForPickupState();
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("250.00"));
        order.setStatus(OrderStatus.READY_FOR_PICKUP);

        OrderContext ctx = new OrderContext(order, com.fooddelivery.common.event.OrderCancelledByRestaurantEvent.builder()
                .orderId(order.getId().toString()).reason("kitchen closed").build(), actionService, ledgerBookkeeper, "UNKNOWN", order.getPaymentMethod());

        state.handleOrderCancelledByRestaurant(ctx);

        assertEquals(OrderStatus.CANCELLED_BY_RESTAURANT, order.getStatus());
        // The context now carries the real event, so the reason on it must reach the order
        // rather than falling through to the generic default.
        assertEquals("kitchen closed", order.getCancellationReason());
        assertTrue(ctx.isRequiresRefund());
    }
}
