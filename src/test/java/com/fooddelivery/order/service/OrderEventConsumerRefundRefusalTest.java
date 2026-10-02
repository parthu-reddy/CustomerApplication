package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.event.EventBinder;
import com.fooddelivery.common.event.OrderCancelledByRestaurantEvent;
import com.fooddelivery.common.event.OrderRejectedEvent;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.order.enums.RefundSource;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.refund.RefundCommand;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A restaurant cancellation whose refund cannot be routed keeps the cancellation. The refusal must
 * come back from {@link RefundService#requestUnlessRefused}: catching one thrown by
 * {@link RefundService#request} does not work, because it crosses the service's transactional proxy
 * and marks this consumer's transaction rollback-only (RefundRefusalInCallerTransactionTest).
 */
class OrderEventConsumerRefundRefusalTest {

    @Test
    void unroutableRefundKeepsTheRestaurantCancellation() {
        Order order = preparingOrder();
        RefundService refunds = mock(RefundService.class);
        when(refunds.requestUnlessRefused(any())).thenReturn(Optional.of("REFUND_STATE_INVALID"));

        assertThatCode(() -> deliver(order, refunds, EventType.ORDER_CANCELLED_BY_RESTAURANT,
                OrderCancelledByRestaurantEvent.class,
                OrderCancelledByRestaurantEvent.builder().orderId(order.getId().toString()).reason("Kitchen closed").build()))
                .doesNotThrowAnyException();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED_BY_RESTAURANT);
        RefundCommand command = requested(refunds);
        assertThat(command.getOrderId()).isEqualTo(order.getId());
        assertThat(command.getSource()).isEqualTo(RefundSource.RESTAURANT);
        assertThat(command.getIdempotencyKey()).isEqualTo("event_" + order.getId() + "_ORDER_CANCELLED_BY_RESTAURANT");
        verify(refunds, never()).request(any());
    }

    /** A rejection is the same restaurant outcome through a different event: full amount, restaurant at fault. */
    @Test
    void routedRejectionRefundIsRequestedOnceAtTheRestaurantsExpense() {
        Order order = preparingOrder();
        RefundService refunds = mock(RefundService.class);
        when(refunds.requestUnlessRefused(any())).thenReturn(Optional.empty());

        deliver(order, refunds, EventType.ORDER_REJECTED, OrderRejectedEvent.class,
                OrderRejectedEvent.builder().orderId(order.getId().toString()).reason("Out of stock").build());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED_BY_RESTAURANT);
        RefundCommand command = requested(refunds);
        assertThat(command.getAmount()).isEqualByComparingTo("53.53");
        assertThat(command.getFaultType()).isEqualTo(FaultType.RESTAURANT_FAULT);
        assertThat(command.getSource()).isEqualTo(RefundSource.RESTAURANT);
        assertThat(command.getIdempotencyKey()).isEqualTo("event_" + order.getId() + "_ORDER_REJECTED");
        verify(refunds, never()).request(any());
    }

    private static Order preparingOrder() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setStatus(OrderStatus.PREPARING);
        order.setTotalAmount(new BigDecimal("53.53"));
        return order;
    }

    private static <E extends com.fooddelivery.common.event.OrderScopedEvent> void deliver(
            Order order, RefundService refunds, EventType type, Class<E> eventClass, E event) {
        EventBinder binder = mock(EventBinder.class);
        when(binder.bindIf(eq(type), eq(type.name()), anyString(), eq(eventClass))).thenReturn(Optional.of(event));
        IOrderRepository orders = mock(IOrderRepository.class);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        doAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                .doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)))
                .when(transaction).execute(any());
        new OrderEventConsumer(binder, mock(IIdempotencyKeyRepository.class), transaction, new ObjectMapper(), orders,
                mock(OrderActionService.class), mock(LedgerBookkeeper.class), refunds)
                .handleOrderEvents("{\"orderId\":\"" + order.getId() + "\",\"eventType\":\"" + type.name() + "\"}",
                        Map.of("eventId", UUID.randomUUID().toString(), "eventType", type.name()));
    }

    private static RefundCommand requested(RefundService refunds) {
        ArgumentCaptor<RefundCommand> command = ArgumentCaptor.forClass(RefundCommand.class);
        verify(refunds).requestUnlessRefused(command.capture());
        return command.getValue();
    }
}
