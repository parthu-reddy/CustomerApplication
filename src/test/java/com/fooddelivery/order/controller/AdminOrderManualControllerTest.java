package com.fooddelivery.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.dto.AdminManualAssignmentRequest;
import com.fooddelivery.order.dto.AdminManualCancellationRequest;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.SupportTicketRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOrderManualControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("a13b0e8d-8d25-4223-a4dd-60ad98a1ac14");
    private static final UUID DRIVER_ID = UUID.fromString("b23b0e8d-8d25-4223-a4dd-60ad98a1ac14");
    private static final UUID ADMIN_ID = UUID.fromString("c23b0e8d-8d25-4223-a4dd-60ad98a1ac14");
    private static final String KEY = "assignment-request-0001";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IOrderRepository orderRepository;
    private OutboxEventRepository outboxEventRepository;
    private AdminOrderManualController controller;

    @BeforeEach
    void setUp() {
        orderRepository = mock(IOrderRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        controller = new AdminOrderManualController(
                orderRepository,
                outboxEventRepository,
                mock(OrderSagaOrchestrator.class),
                mock(SupportTicketRepository.class),
                objectMapper);
        when(outboxEventRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void assignmentCreatesOneAuditedOutboxEventAndPersistsCurrentOperation() throws Exception {
        Order order = manualOrder();
        when(orderRepository.findLockedById(ORDER_ID)).thenReturn(Optional.of(order));

        var response = controller.assignDriver(
                ORDER_ID,
                admin(),
                KEY,
                new AdminManualAssignmentRequest(DRIVER_ID, "Rider confirmed nearby"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(order.getManualInterventionRequestedDriverId()).isEqualTo(DRIVER_ID);
        assertThat(order.getManualInterventionRequestedBy()).isEqualTo(ADMIN_ID);
        assertThat(order.getManualInterventionReason()).isEqualTo("Rider confirmed nearby");
        assertThat(order.getManualInterventionOperationId())
                .isEqualTo(operation("assign", KEY));

        org.mockito.ArgumentCaptor<OutboxEventEntity> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEventEntity outboxEvent = eventCaptor.getValue();
        assertThat(outboxEvent.getIdempotencyKey()).isEqualTo(operation("assign", KEY));
        assertThat(outboxEvent.getEventType())
                .isEqualTo(com.fooddelivery.common.constants.EventType.FORCE_ASSIGN_DRIVER);
        com.fooddelivery.common.event.ForceAssignDriverEvent event = objectMapper.readValue(
                outboxEvent.getPayload(), com.fooddelivery.common.event.ForceAssignDriverEvent.class);
        assertThat(event.getActorId()).isEqualTo(ADMIN_ID.toString());
        assertThat(event.getDriverId()).isEqualTo(DRIVER_ID.toString());
        assertThat(event.getReason()).isEqualTo("Rider confirmed nearby");
        assertThat(event.getOperationId()).isEqualTo(operation("assign", KEY));
    }

    @Test
    void matchingAssignmentReplayReturnsSuccessWithoutAnotherOutboxWrite() throws Exception {
        Order order = manualOrder();
        when(orderRepository.findLockedById(ORDER_ID)).thenReturn(Optional.of(order));
        OutboxEventEntity prior = OutboxEventEntity.builder()
                .eventType(com.fooddelivery.common.constants.EventType.FORCE_ASSIGN_DRIVER)
                .payload(objectMapper.writeValueAsString(com.fooddelivery.common.event.ForceAssignDriverEvent.builder()
                        .orderId(ORDER_ID.toString())
                        .driverId(DRIVER_ID.toString())
                        .actorId(ADMIN_ID.toString())
                        .reason("Rider confirmed nearby")
                        .operationId(operation("assign", KEY))
                        .build()))
                .build();
        when(outboxEventRepository.findByIdempotencyKey(operation("assign", KEY)))
                .thenReturn(Optional.of(prior));

        var response = controller.assignDriver(
                ORDER_ID,
                admin(),
                KEY,
                new AdminManualAssignmentRequest(DRIVER_ID, "Rider confirmed nearby"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).contains("Idempotent replay");
        verify(outboxEventRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void conflictingReplayIsRejectedInsteadOfReusingTheIdempotencyKey() throws Exception {
        Order order = manualOrder();
        when(orderRepository.findLockedById(ORDER_ID)).thenReturn(Optional.of(order));
        OutboxEventEntity prior = OutboxEventEntity.builder()
                .eventType(com.fooddelivery.common.constants.EventType.FORCE_ASSIGN_DRIVER)
                .payload(objectMapper.writeValueAsString(com.fooddelivery.common.event.ForceAssignDriverEvent.builder()
                        .orderId(ORDER_ID.toString())
                        .driverId(UUID.randomUUID().toString())
                        .actorId(ADMIN_ID.toString())
                        .reason("Different rider")
                        .operationId(operation("assign", KEY))
                        .build()))
                .build();
        when(outboxEventRepository.findByIdempotencyKey(operation("assign", KEY)))
                .thenReturn(Optional.of(prior));

        var response = controller.assignDriver(
                ORDER_ID,
                admin(),
                KEY,
                new AdminManualAssignmentRequest(DRIVER_ID, "Rider confirmed nearby"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void competingManualActionIsRejectedUntilTheCurrentOperationHasAResult() {
        Order order = manualOrder();
        order.setManualInterventionOperationId("admin-manual:assign:already-processing");
        when(orderRepository.findLockedById(ORDER_ID)).thenReturn(Optional.of(order));

        var response = controller.assignDriver(
                ORDER_ID,
                admin(),
                "second-assignment-request-0001",
                new AdminManualAssignmentRequest(DRIVER_ID, "A second rider is nearby"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("still being processed");
        verify(orderRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void durableFailureReopensTheOrderForANewManualDecision() {
        Order order = manualOrder();
        order.setManualInterventionOperationId("admin-manual:assign:rejected-operation");
        order.setManualInterventionFailureCode("DRIVER_NOT_ONLINE");
        when(orderRepository.findLockedById(ORDER_ID)).thenReturn(Optional.of(order));

        var response = controller.assignDriver(
                ORDER_ID,
                admin(),
                "replacement-assignment-request-0001",
                new AdminManualAssignmentRequest(DRIVER_ID, "Replacement rider is now ready"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(order.getManualInterventionFailureCode()).isNull();
        verify(orderRepository).save(order);
        verify(outboxEventRepository).save(any());
    }

    @Test
    void forceCancelUsesTheSameAuditedCancellationWorkflow() throws Exception {
        Order order = manualOrder();
        when(orderRepository.findLockedById(ORDER_ID)).thenReturn(Optional.of(order));

        var response = controller.forceCancelOrder(
                ORDER_ID,
                admin(),
                "force-cancel-request-0001",
                new AdminManualCancellationRequest("Dispatch exhausted all safe candidates"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(order.getManualInterventionRequestedDriverId()).isNull();
        assertThat(order.getManualInterventionOperationId())
                .isEqualTo(operation("force-cancel", "force-cancel-request-0001"));
        org.mockito.ArgumentCaptor<OutboxEventEntity> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType())
                .isEqualTo(com.fooddelivery.common.constants.EventType.ORDER_CANCELLED_BY_ADMIN);
        com.fooddelivery.common.event.OrderCancelledByAdminEvent event = objectMapper.readValue(
                eventCaptor.getValue().getPayload(),
                com.fooddelivery.common.event.OrderCancelledByAdminEvent.class);
        assertThat(event.getReason()).isEqualTo("Dispatch exhausted all safe candidates");
        assertThat(event.getActorId()).isEqualTo(ADMIN_ID.toString());
    }

    @Test
    void outboxFailurePropagatesSoTheTransactionCanRollBack() {
        Order order = manualOrder();
        when(orderRepository.findLockedById(ORDER_ID)).thenReturn(Optional.of(order));
        when(outboxEventRepository.save(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> controller.assignDriver(
                ORDER_ID,
                admin(),
                KEY,
                new AdminManualAssignmentRequest(DRIVER_ID, "Rider confirmed nearby")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Order manualOrder() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setCustomerId(UUID.randomUUID());
        order.setDispatchCityId("BLR");
        order.setDeliveryStatus(DeliveryStatus.MANUAL_INTERVENTION_REQUIRED);
        return order;
    }

    private java.security.Principal admin() {
        return () -> ADMIN_ID.toString();
    }

    private String operation(String action, String key) {
        return "admin-manual:" + action + ":" + ORDER_ID + ":" + ADMIN_ID + ":" + key;
    }
}
