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
    }

    private final IIdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final IOrderRepository orderRepository;
    private final OrderActionService orderActionService;
    private final OrderRefundService orderRefundService;

    public OrderEventConsumer(IIdempotencyKeyRepository idempotencyKeyRepository,
                              TransactionTemplate transactionTemplate,
                              ObjectMapper objectMapper,
                              IOrderRepository orderRepository,
                              OrderActionService orderActionService,
                              OrderRefundService orderRefundService) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.orderActionService = orderActionService;
        this.orderRefundService = orderRefundService;
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000))
    @KafkaListener(topics = KafkaConstants.TOPIC_ORDER_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY)
    public void handleOrderEvents(String payload, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        log.info("OrderEventConsumer received event: {}", payload);
        String extractedEventId = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
        final String resolvedEventId;
        if (extractedEventId == null) {
            resolvedEventId = UUID.nameUUIDFromBytes(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            log.warn("eventId header missing in handleOrderEvents. Using deterministic payload hash as eventId: {}", resolvedEventId);
        } else {
            resolvedEventId = extractedEventId;
        }

        String idempotencyKeyStr = "processed_event:" + resolvedEventId;

        try {
            int retries = 0;
            boolean success = false;
            while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
                try {
                    transactionTemplate.execute(status -> {
                        if (idempotencyKeyRepository.existsById(idempotencyKeyStr)) {
                            log.info("Duplicate event ignored: {}", idempotencyKeyStr);
                            return null;
                        }
                        idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKeyStr));

                        try {
                            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);
                            String eventType = com.fooddelivery.common.util.KafkaHeaderUtils.extractEventType(headers, rootNode);
                            String orderIdStr = rootNode.path("orderId").asText(null);
                            if (orderIdStr == null && rootNode.has("id")) {
                                orderIdStr = rootNode.get("id").asText();
                            }
                            if (orderIdStr == null || eventType == null) {
                                log.warn("Missing orderId or eventType. Ignored.");
                                return null;
                            }
                            UUID orderId = UUID.fromString(orderIdStr);
                            Order order = orderRepository.findById(orderId).orElse(null);
                            if (order == null) {
                                log.warn("Order {} not found, skipping event", orderId);
                                return null;
                            }
                            com.fooddelivery.order.service.state.OrderContext context = new com.fooddelivery.order.service.state.OrderContext(order, rootNode, orderActionService);
                            com.fooddelivery.order.service.state.OrderState state = com.fooddelivery.order.service.state.OrderStateFactory.getState(order.getStatus());
                            try {
                                java.util.function.BiConsumer<com.fooddelivery.order.service.state.OrderState, com.fooddelivery.order.service.state.OrderContext> handler = EVENT_HANDLERS.get(eventType);
                                if (handler != null) {
                                    handler.accept(state, context);
                                } else if (EventType.ORDER_STATUS_UPDATED.name().equals(eventType)) {
                                    String updateStatus = rootNode.path("status").asText(null);
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
                                orderRefundService.processRefund(order);
                            }
                            return null;
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to process order event inner", e);
                        }
                    });
                    success = true;
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                    log.warn("Optimistic locking failure in handleOrderEvents, delegating to Kafka retry.");
                    throw e;
                } catch (Exception e) {
                    log.error("Error processing order event", e);
                    throw new RuntimeException("Failed to process order event", e);
                }
            }
        } catch (Exception e) {
            throw e;
        }
    }

    @org.springframework.kafka.annotation.DltHandler
    public void handleDlt(String message, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        log.error("DLT processing: Message exhausted all retries in CustomerApplication. Message: {}, Headers: {}", message, headers);
    }
}
