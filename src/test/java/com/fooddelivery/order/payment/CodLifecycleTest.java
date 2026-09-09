package com.fooddelivery.order.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.impl.CreatedState;
import com.fooddelivery.order.service.state.impl.HandedOverState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Cash on delivery is not paid at checkout. It was previously marked SUCCESS there, which told
 * {@code RefundService} money had been taken -- so cancelling an uncollected COD order issued real
 * store credit for cash the platform never received.
 *
 * These tests pin the two states that decide it: nothing is collected when the order is placed, and
 * the cash becomes a receivable only when the rider delivers.
 */
public class CodLifecycleTest {

    private OrderActionService actionService;
    private LedgerBookkeeper bookkeeper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        actionService = mock(OrderActionService.class);
        bookkeeper = mock(LedgerBookkeeper.class);
        objectMapper = new ObjectMapper();
    }

    private Order order(PaymentMethod method) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("420.00"));
        order.setPaymentMethod(method);
        order.setStatus(OrderStatus.CREATED);
        return order;
    }

    private OrderContext ctx(Order order, String gateway) {
        return new OrderContext(order, objectMapper.createObjectNode(), actionService, bookkeeper, gateway);
    }

    @Test
    void codOrderIsPendingCollectionAtCheckoutAndBooksNoCapture() {
        Order order = order(PaymentMethod.COD);

        new CreatedState().handlePaymentSuccess(ctx(order, null));

        assertEquals(PaymentIntentStatus.PENDING_COLLECTION, order.getPaymentStatus(),
                "COD must not be marked paid before the rider collects the cash");
        verify(actionService).updatePaymentIntentStatus(order.getId(), PaymentIntentStatus.PENDING_COLLECTION);
        verify(bookkeeper, never()).bookPaymentCaptured(any(), any());
        verify(actionService).emitOrderPlacedCodEvent(order);
        verify(actionService, never()).emitOrderPaidEvent(any());
        assertEquals(OrderStatus.PENDING_ACCEPTANCE, order.getStatus(),
                "the restaurant still has to be told to cook it");
    }

    @Test
    void gatewayOrderIsPaidAtCheckoutAndBooksTheCapture() {
        Order order = order(PaymentMethod.CARD);

        new CreatedState().handlePaymentSuccess(ctx(order, "RAZORPAY"));

        assertEquals(PaymentIntentStatus.SUCCESS, order.getPaymentStatus());
        verify(actionService).updatePaymentIntentStatus(order.getId(), PaymentIntentStatus.SUCCESS);
        verify(bookkeeper).bookPaymentCaptured(order, "RAZORPAY");
        verify(actionService).emitOrderPaidEvent(order);
        verify(actionService, never()).emitOrderPlacedCodEvent(any());
    }

    @Test
    void deliveringACodOrderCollectsTheCash() {
        Order order = order(PaymentMethod.COD);
        order.setStatus(OrderStatus.HANDED_OVER);
        order.setDeliveryExecutiveId(UUID.randomUUID());

        new HandedOverState().handleOrderDelivered(ctx(order, null));

        assertEquals(DeliveryStatus.DELIVERED, order.getDeliveryStatus());
        verify(bookkeeper).bookCashCollected(order);
        assertEquals(PaymentIntentStatus.COLLECTED, order.getPaymentStatus(),
                "collected cash is distinct from a gateway capture");
        verify(actionService).updatePaymentIntentStatus(order.getId(), PaymentIntentStatus.COLLECTED);
    }

    @Test
    void deliveringAGatewayOrderCollectsNoCash() {
        Order order = order(PaymentMethod.CARD);
        order.setStatus(OrderStatus.HANDED_OVER);
        order.setDeliveryExecutiveId(UUID.randomUUID());

        new HandedOverState().handleOrderDelivered(ctx(order, "RAZORPAY"));

        verify(bookkeeper).bookDelivered(order);
        verify(bookkeeper, never()).bookCashCollected(any());
    }

    @Test
    void deliveryAlwaysBooksTheOrderEconomics() {
        Order order = order(PaymentMethod.COD);
        order.setStatus(OrderStatus.HANDED_OVER);
        order.setDeliveryExecutiveId(UUID.randomUUID());

        new HandedOverState().handleOrderDelivered(ctx(order, null));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(bookkeeper).bookDelivered(captor.capture());
        assertEquals(order.getId(), captor.getValue().getId());
    }
}
