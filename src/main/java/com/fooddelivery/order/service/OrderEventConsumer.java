package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.entity.IdempotencyKey;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class OrderEventConsumer {

    private static final java.util.Map<String, java.util.function.BiConsumer<com.fooddelivery.order.service.state.OrderState, com.fooddelivery.order.service.state.OrderContext>> EVENT_HANDLERS = new java.util.HashMap<>();
    static {
        EVENT_HANDLERS.put(EventType.ORDER_DELIVERED.name(), com.fooddelivery.order.service.state.OrderState::handleOrderDelivered);
        EVENT_HANDLERS.put(EventType.ORDER_CANCELLED_BY_RESTAURANT.name(), com.fooddelivery.order.service.state.OrderState::handleOrderCancelledByRestaurant);
        EVENT_HANDLERS.put(EventType.ORDER_CANCELLED_BY_ADMIN.name(), com.fooddelivery.order.service.state.OrderState::handleOrderCancelledByAdmin);
        EVENT_HANDLERS.put(EventType.ORDER_REJECTED.name(), com.fooddelivery.order.service.state.OrderState::handleOrderCancelledByRestaurant);
        EVENT_HANDLERS.put(EventType.DRIVER_ASSIGNED.name(), com.fooddelivery.order.service.state.OrderState::handleDriverAssigned);
        EVENT_HANDLERS.put(EventType.MANUAL_INTERVENTION_REQUIRED.name(), com.fooddelivery.order.service.state.OrderState::handleManualInterventionRequired);
        EVENT_HANDLERS.put(EventType.DELIVERY_FAILED.name(), com.fooddelivery.order.service.state.OrderState::handleDeliveryFailed);
        EVENT_HANDLERS.put(EventType.ORDER_DELAY_APPROVAL_REQUESTED.name(), com.fooddelivery.order.service.state.OrderState::handleDelayApprovalRequested);
        EVENT_HANDLERS.put(EventType.ORDER_DELAY_REJECTED.name(), com.fooddelivery.order.service.state.OrderState::handleDelayRejected);
        EVENT_HANDLERS.put(EventType.ORDER_ACCEPTED.name(), com.fooddelivery.order.service.state.OrderState::handleOrderAccepted);
        EVENT_HANDLERS.put(EventType.ORDER_PREPARING.name(), com.fooddelivery.order.service.state.OrderState::handleOrderPreparing);
        EVENT_HANDLERS.put(EventType.ORDER_READY.name(), com.fooddelivery.order.service.state.OrderState::handleOrderReady);
        EVENT_HANDLERS.put(EventType.ORDER_AT_RESTAURANT.name(), com.fooddelivery.order.service.state.OrderState::handleDriverAtRestaurant);
        EVENT_HANDLERS.put(EventType.DISPATCH_FAILED.name(), com.fooddelivery.order.service.state.OrderState::handleDispatchFailed);
    }

    
    /**
     * The event types this service binds, and the class each binds to.
     *
     * <p>Typed as {@code OrderScopedEvent} so the listener takes the order id off a bound event
     * without reflection, and so an entry whose class is not order-scoped does not compile.
     */
    private static final java.util.Map<String, Class<? extends com.fooddelivery.common.event.OrderScopedEvent>>
            EVENT_CLASSES = new java.util.HashMap<>();
    static {
        EVENT_CLASSES.put(EventType.ORDER_DELIVERED.name(), com.fooddelivery.common.event.DeliveredEvent.class);
        EVENT_CLASSES.put(EventType.ORDER_CANCELLED_BY_RESTAURANT.name(), com.fooddelivery.common.event.OrderCancelledByRestaurantEvent.class);
        EVENT_CLASSES.put(EventType.ORDER_CANCELLED_BY_ADMIN.name(), com.fooddelivery.common.event.OrderCancelledByAdminEvent.class);
        EVENT_CLASSES.put(EventType.ORDER_REJECTED.name(), com.fooddelivery.common.event.OrderRejectedEvent.class);
        EVENT_CLASSES.put(EventType.DRIVER_ASSIGNED.name(), com.fooddelivery.common.event.DriverAssignedEvent.class);
        EVENT_CLASSES.put(EventType.MANUAL_INTERVENTION_REQUIRED.name(), com.fooddelivery.common.event.ManualInterventionRequiredEvent.class);
        EVENT_CLASSES.put(EventType.DELIVERY_FAILED.name(), com.fooddelivery.common.event.DeliveryFailedEvent.class);
        EVENT_CLASSES.put(EventType.ORDER_DELAY_APPROVAL_REQUESTED.name(), com.fooddelivery.common.event.OrderDelayApprovalRequestedEvent.class);
        EVENT_CLASSES.put(EventType.ORDER_DELAY_REJECTED.name(), com.fooddelivery.common.event.OrderDelayRejectedEvent.class);
        EVENT_CLASSES.put(EventType.ORDER_ACCEPTED.name(), com.fooddelivery.common.event.OrderAcceptedEvent.class);
        EVENT_CLASSES.put(EventType.ORDER_PREPARING.name(), com.fooddelivery.common.event.OrderPreparingEvent.class);
        EVENT_CLASSES.put(EventType.ORDER_READY.name(), com.fooddelivery.common.event.OrderReadyEvent.class);
        EVENT_CLASSES.put(EventType.ORDER_AT_RESTAURANT.name(), com.fooddelivery.common.event.DriverAtRestaurantEvent.class);
        EVENT_CLASSES.put(EventType.ORDER_STATUS_UPDATED.name(), com.fooddelivery.common.event.OrderStatusUpdatedEvent.class);
        EVENT_CLASSES.put(EventType.DISPATCH_FAILED.name(), com.fooddelivery.common.event.DispatchFailedEvent.class);
        // ORDER_DRIVER_REJECTED clears the assigned driver and emits a status sync further down,
        // but had no entry here -- so the listener bound nothing, found no orderId, and logged
        // "Missing orderId. Ignored." A driver rejection never reached the order. The class
        // already existed; only the mapping was missing.
        EVENT_CLASSES.put(EventType.ORDER_DRIVER_REJECTED.name(), com.fooddelivery.common.event.OrderDriverRejectedEvent.class);
    }

    private final com.fooddelivery.common.event.EventBinder eventBinder;
    private final IIdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final com.fooddelivery.order.ledger.LedgerBookkeeper ledgerBookkeeper;
    private final com.fooddelivery.order.refund.RefundService refundService;



    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000), exclude = {com.fooddelivery.common.event.EventBindingException.class}, traversingCauses = "true")
    @KafkaListener(topics = KafkaConstants.TOPIC_ORDER_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY + "-ordereventconsumer")
    public void handleOrderEvents(String payload, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        String extractedEventId = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
        if (extractedEventId == null) {
            throw new IllegalArgumentException("Missing eventId header");
        }
        final String resolvedEventId = extractedEventId;
        String receivedEventType = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventType");
        log.info("ORDER_EVENT_RECEIVED eventId={} eventType={} payloadBytes={}",
                resolvedEventId, receivedEventType, payload == null ? 0 : payload.length());

        String idempotencyKeyStr = "processed_event:" + resolvedEventId;

        // No retry loop here. This used to read
        //     int retries = 0; boolean success = false;
        //     while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) { ... }
        // with `retries` never incremented and every catch rethrowing, so the body ran exactly once
        // by construction while reading as if it retried three times. Retrying is the listener's
        // job: the optimistic-lock catch below rethrows so the retry topic handles it.
        try {
            transactionTemplate.execute(status -> {
                if (idempotencyKeyRepository.existsById(idempotencyKeyStr)) {
                    log.info("ORDER_EVENT_DUPLICATE eventId={} eventType={}", resolvedEventId, receivedEventType);
                    return null;
                }
                idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKeyStr));

                try {
                    com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);
                    String eventType = com.fooddelivery.common.util.KafkaHeaderUtils.extractEventType(headers, rootNode);
                    if (eventType == null) {
                        log.warn("Missing eventType. Ignored.");
                        return null;
                    }
                    Class<? extends com.fooddelivery.common.event.OrderScopedEvent> targetClass =
                            EVENT_CLASSES.get(eventType);
                    if (targetClass == null) {
                        // order-events carries every order event on the platform; most are not this
                        // consumer's business. Ignoring is the correct outcome, not a DLT entry.
                        log.info("Event {} not handled by CustomerApplication. Ignoring.", eventType);
                        return null;
                    }
                    final EventType eType;
                    try {
                        eType = EventType.valueOf(eventType);
                    } catch (IllegalArgumentException e) {
                        // Unreachable while EVENT_CLASSES is keyed on EventType.name(), but a
                        // header carrying a type this enum does not know must not poison the
                        // partition.
                        log.info("Unknown event type {} on order-events. Ignoring.", eventType);
                        return null;
                    }
                    // Empty means "different event type", which cannot happen here -- eType is
                    // derived from eventType. A malformed body or a violated @NotNull throws out
                    // of bindIf and feeds the retry/DLT path.
                    com.fooddelivery.common.event.OrderScopedEvent typedEvent =
                            eventBinder.bindIf(eType, eventType, payload, targetClass)
                                    .orElseThrow(() -> new IllegalStateException(
                                            "bindIf returned empty for " + eventType
                                                    + " despite an exact event-type match"));

                    UUID orderId = typedEvent.orderUuid();
                    if (orderId == null) {
                        log.warn("Missing orderId. Ignored.");
                        return null;
                    }
                    Order order = orderRepository.findById(orderId).orElse(null);
                    if (order == null) {
                        log.warn("Order {} not found, skipping event", orderId);
                        return null;
                    }

                    com.fooddelivery.order.service.state.OrderContext context = new com.fooddelivery.order.service.state.OrderContext(
                            order, typedEvent, orderActionService, ledgerBookkeeper,
                            null /* no gateway: order-lifecycle events book no capture */,
                            order.getPaymentMethod());
                    com.fooddelivery.order.service.state.OrderState state = com.fooddelivery.order.service.state.OrderStateFactory.getState(order.getStatus());
                    try {
                        java.util.function.BiConsumer<com.fooddelivery.order.service.state.OrderState, com.fooddelivery.order.service.state.OrderContext> handler = EVENT_HANDLERS.get(eventType);
                        if (handler != null) {
                            handler.accept(state, context);
                        } else if (EventType.ORDER_STATUS_UPDATED.name().equals(eventType)) {
                            String updateStatus = ((com.fooddelivery.common.event.OrderStatusUpdatedEvent) typedEvent).getStatus();
                            if (EventType.DELIVERY_FAILED.name().equals(updateStatus)) {
                                state.handleDeliveryFailed(context);
                            } else {
                                state.handleStatusUpdate(context);
                            }
                        } else if (EventType.ORDER_DRIVER_REJECTED.name().equals(eventType)) {
                            log.info("Driver rejected/timed out ping for Order {}. Redispatching will be handled by DeliveryExecutiveApplication.", orderId);
                            order.setDeliveryExecutiveId(null);
                            orderRepository.save(order);
                            orderActionService.emitOrderStatusSyncEvent(order.getId(), order.getStatus());
                        } else {
                            log.warn("Unmapped event type {} for Order {}. Ignoring.", eventType, orderId);
                        }
                    } catch (com.fooddelivery.common.exception.IllegalStateTransitionException e) {
                        log.error("ILLEGAL_STATE_TRANSITION: {}", e.getMessage());
                        orderActionService.emitOrderStatusSyncEvent(order.getId(), order.getStatus());
                    }
                    if (context.isRequiresRefund()) {
                        com.fooddelivery.order.refund.RefundCommand cmd = com.fooddelivery.order.refund.RefundCommand.builder()
                                .orderId(order.getId())
                                .amount(order.getTotalAmount())
                                .faultType(faultTypeFor(eventType))
                                // No destination: RefundService is the single routing authority.
                                .initiatorType(com.fooddelivery.order.enums.InitiatorType.SYSTEM)
                                .reasonCode(eventType)
                                .idempotencyKey("event_" + order.getId() + "_" + eventType)
                                .build();
                        requestRefundWithoutPoisoningTheEvent(order.getId(), cmd);
                    }
                    return null;
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process order event inner", e);
                }
            });
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic locking failure in handleOrderEvents, delegating to Kafka retry.");
            throw e;
        } catch (Exception e) {
            log.error("Error processing order event", e);
            throw new RuntimeException("Failed to process order event", e);
        }
    }

    @org.springframework.kafka.annotation.DltHandler
    public void handleDlt(String message, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        log.error("ORDER_EVENT_DLT eventId={} eventType={} payloadBytes={} exception={} replay={}",
                com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId"),
                com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventType"),
                message == null ? 0 : message.length(),
                com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers,
                        org.springframework.kafka.support.KafkaHeaders.EXCEPTION_MESSAGE),
                com.fooddelivery.common.util.KafkaHeaderUtils.deadLetterPosition(headers));
    }

    /**
     * A refund the matrix refuses must not roll back the state transition that triggered it.
     *
     * <p>The refund request runs inside this listener's transaction. When routing throws -- an
     * intent still INITIATED, so nobody knows whether the gateway took money -- the exception
     * unwinds the transaction, the delivery-failed or cancellation is lost, and Kafka redelivers
     * the same event forever, blocking every other order on the partition. The state change is the
     * more important of the two and is kept; the refund is left loudly unmade for an operator.
     */
    /**
     * Who is at fault for a refund raised by an event, which is what decides the clawback.
     *
     * <p>Every event-driven refund used to be booked as {@code UNKNOWN}, so a dispatch failure --
     * the platform's own failure to find a rider -- was settled the same way as a restaurant
     * cancellation.
     */
    private static com.fooddelivery.order.enums.FaultType faultTypeFor(String eventType) {
        if (eventType == null) {
            return com.fooddelivery.order.enums.FaultType.UNKNOWN;
        }
        if (EventType.DISPATCH_FAILED.name().equals(eventType)
                || EventType.DELIVERY_FAILED.name().equals(eventType)
                || EventType.MANUAL_INTERVENTION_REQUIRED.name().equals(eventType)) {
            return com.fooddelivery.order.enums.FaultType.PLATFORM_FAULT;
        }
        if (EventType.ORDER_REJECTED.name().equals(eventType)
                || EventType.ORDER_CANCELLED_BY_RESTAURANT.name().equals(eventType)
                || EventType.ORDER_DELAY_REJECTED.name().equals(eventType)) {
            return com.fooddelivery.order.enums.FaultType.RESTAURANT_FAULT;
        }
        return com.fooddelivery.order.enums.FaultType.UNKNOWN;
    }

    private void requestRefundWithoutPoisoningTheEvent(java.util.UUID orderId,
                                                       com.fooddelivery.order.refund.RefundCommand cmd) {
        try {
            refundService.request(cmd);
        } catch (IllegalStateException e) {
            log.error("REFUND_NOT_ROUTED: order {} needs a refund but it could not be routed ({}). "
                    + "The order state change is kept; resolve this from the admin refund queue.",
                    orderId, e.getMessage());
        }
    }
}
