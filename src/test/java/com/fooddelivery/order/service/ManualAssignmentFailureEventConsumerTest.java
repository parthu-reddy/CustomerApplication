package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.event.EventBinder;
import com.fooddelivery.common.event.ManualAssignmentFailedEvent;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualAssignmentFailureEventConsumerTest {

    private static final UUID ORDER_ID = UUID.fromString("f894d82e-b6d8-4b5f-ae40-3103ec01b8bd");
    private static final UUID DRIVER_ID = UUID.fromString("20e4e06b-5836-49f0-a206-f3b36dfc3f0c");
    private static final UUID ACTOR_ID = UUID.fromString("ee288caa-9b23-46cf-a821-c9b7d64b44d2");
    private static final String OPERATION = "admin-manual:assign:operation-0001";

    @Test
    @SuppressWarnings("unchecked")
    void currentFailureIsPersistedForTheAdminInterventionQueue() {
        EventBinder eventBinder = mock(EventBinder.class);
        IIdempotencyKeyRepository idempotency = mock(IIdempotencyKeyRepository.class);
        IOrderRepository orders = mock(IOrderRepository.class);
        Order order = manualOrder(OPERATION);
        ManualAssignmentFailedEvent failure = failure(OPERATION, "DRIVER_NOT_ONLINE");
        when(idempotency.existsById(anyString())).thenReturn(false);
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(eventBinder.bindIf(eq(EventType.MANUAL_ASSIGNMENT_FAILED),
                eq(EventType.MANUAL_ASSIGNMENT_FAILED.name()), anyString(),
                eq(ManualAssignmentFailedEvent.class))).thenReturn(Optional.of(failure));

        OrderEventConsumer consumer = consumer(eventBinder, idempotency, orders);
        consumer.handleOrderEvents("{\"orderId\":\"" + ORDER_ID + "\",\"eventType\":\"MANUAL_ASSIGNMENT_FAILED\"}",
                headers("manual-failure-current"));

        assertThat(order.getManualInterventionFailureCode()).isEqualTo("DRIVER_NOT_ONLINE");
        assertThat(order.getManualInterventionFailedAt()).isEqualTo(Instant.ofEpochMilli(1_726_000_000_000L));
        verify(orders).save(order);
    }

    @Test
    @SuppressWarnings("unchecked")
    void staleFailureCannotOverwriteANewerManualIntervention() {
        EventBinder eventBinder = mock(EventBinder.class);
        IIdempotencyKeyRepository idempotency = mock(IIdempotencyKeyRepository.class);
        IOrderRepository orders = mock(IOrderRepository.class);
        Order order = manualOrder("admin-manual:assign:newer-operation");
        ManualAssignmentFailedEvent failure = failure(OPERATION, "DRIVER_NOT_ONLINE");
        when(idempotency.existsById(anyString())).thenReturn(false);
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(eventBinder.bindIf(eq(EventType.MANUAL_ASSIGNMENT_FAILED),
                eq(EventType.MANUAL_ASSIGNMENT_FAILED.name()), anyString(),
                eq(ManualAssignmentFailedEvent.class))).thenReturn(Optional.of(failure));

        OrderEventConsumer consumer = consumer(eventBinder, idempotency, orders);
        consumer.handleOrderEvents("{\"orderId\":\"" + ORDER_ID + "\",\"eventType\":\"MANUAL_ASSIGNMENT_FAILED\"}",
                headers("manual-failure-stale"));

        assertThat(order.getManualInterventionFailureCode()).isNull();
        verify(orders, never()).save(any());
    }

    private OrderEventConsumer consumer(
            EventBinder eventBinder,
            IIdempotencyKeyRepository idempotency,
            IOrderRepository orders) {
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
        }).when(transaction).execute(any());
        return new OrderEventConsumer(
                eventBinder,
                idempotency,
                transaction,
                new ObjectMapper(),
                orders,
                mock(OrderActionService.class),
                mock(LedgerBookkeeper.class),
                mock(RefundService.class));
    }

    private Order manualOrder(String operationId) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setStatus(OrderStatus.ACCEPTED);
        order.setDeliveryStatus(DeliveryStatus.MANUAL_INTERVENTION_REQUIRED);
        order.setManualInterventionOperationId(operationId);
        return order;
    }

    private ManualAssignmentFailedEvent failure(String operationId, String reasonCode) {
        return ManualAssignmentFailedEvent.builder()
                .orderId(ORDER_ID.toString())
                .operationId(operationId)
                .actorId(ACTOR_ID.toString())
                .driverId(DRIVER_ID.toString())
                .reasonCode(reasonCode)
                .timestamp(1_726_000_000_000L)
                .build();
    }

    private Map<String, Object> headers(String eventId) {
        return Map.of("eventId", eventId, "eventType", EventType.MANUAL_ASSIGNMENT_FAILED.name());
    }
}
