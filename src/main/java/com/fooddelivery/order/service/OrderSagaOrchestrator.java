package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.event.OrderCreatedEvent;
import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.exception.OrderProcessingException;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.AccountType;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.retry.annotation.Backoff;
import org.springframework.messaging.handler.annotation.Payload;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Service
public class OrderSagaOrchestrator {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderSagaOrchestrator.class);
    private static final String REFUND_TX_PREFIX = "REFUND_";
    private static final String FIELD_ORDER_ID = "orderId";
    private static final String FIELD_GATEWAY_ORDER_ID = "gatewayOrderId";
    private static final String FIELD_EVENT_TYPE = "eventType";
    private static final String FIELD_FAILURE_REASON = "failureReason";
    private final IOrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IPaymentIntentRepository paymentIntentRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
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
        EVENT_HANDLERS.put(EventType.ORDER_DELIVERED.name(), com.fooddelivery.order.service.state.OrderState::handleOrderDelivered);
    }

    private final com.fooddelivery.customer.client.PaymentClient paymentClient;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final com.fooddelivery.order.service.state.OrderActionService orderActionService;
    private final StringRedisTemplate redisTemplate;
    // We assume the system account ID for the platform is a fixed UUID for this prototype
    private static final UUID PLATFORM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Transactional
    public Order startOrderSaga(Order order) {
        java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        String otp = String.format("%06d", secureRandom.nextInt(1000000));
        order.setPickupOtp(otp);
        // Generate delivery OTP
        String deliveryOtp = String.format("%06d", secureRandom.nextInt(1000000));
        order.setOtp(deliveryOtp);
        Order savedOrder = orderRepository.save(order);
        OrderCreatedEvent event = OrderCreatedEvent.builder().orderId(savedOrder.getId()).customerId(savedOrder.getCustomerId()).restaurantId(savedOrder.getRestaurantId()).totalAmount(savedOrder.getTotalAmount()).deliveryLat(savedOrder.getDeliveryLat()).deliveryLng(savedOrder.getDeliveryLng()).deliveryAddress(savedOrder.getDeliveryAddress()).pickupOtp(savedOrder.getPickupOtp()).deliveryOtp(savedOrder.getOtp()).build();
        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(savedOrder.getId().toString()).eventType(EventType.ORDER_CREATED).payload(objectMapper.writeValueAsString(event)).createdAt(LocalDateTime.now()).build();
            log.info("Triggering event: {} for order: {}", EventType.ORDER_CREATED.name(), savedOrder.getId());
            outboxEventRepository.save(outboxEvent);
            log.info("Order created and outbox event saved for Order ID: {}", savedOrder.getId());
        } catch (Exception e) {
            log.error("Failed to serialize OrderCreatedEvent or save outbox", e);
            throw new OrderProcessingException("Failed to process order creation", e);
        }
        return savedOrder;
    }

    @Transactional
    public void saveStateAndEvent(Order order, com.fooddelivery.common.event.OutboxEvent event) {
        orderRepository.save(order);
        OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(event.getAggregateType()).aggregateId(event.getAggregateId()).eventType(event.getEventType()).payload(event.getPayload()).createdAt(LocalDateTime.now()).build();
        log.info("Triggering event: {} for aggregate: {}", event.getEventType(), event.getAggregateId());
        outboxEventRepository.save(outboxEvent);
        log.info("Order state and outbox event saved for Order ID: {}", order.getId());
    }

    // Listens to Kafka 'payment-events' topic for events published by external Payment Service
    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000))
    @KafkaListener(topics = KafkaConstants.TOPIC_PAYMENT_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY)
    public void handlePaymentEvents(String payload, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        log.info("Received Payment Event: {}", payload);
        String extractedEventId = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
        final String resolvedEventId;
        if (extractedEventId == null) {
            resolvedEventId = UUID.nameUUIDFromBytes(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            log.warn("eventId header missing. Using deterministic payload hash as eventId: {}", resolvedEventId);
        } else {
            resolvedEventId = extractedEventId;
        }
        boolean isNew = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent("processed_event:" + resolvedEventId, "1", java.time.Duration.ofDays(7)));
        if (!isNew) {
            log.info("Duplicate event ignored: {}", resolvedEventId);
            return;
        }
        try {
            int retries = 0;
            boolean success = false;
            while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
                try {
                    Order orderToRefund = transactionTemplate.execute(status -> {
                        try {
                            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);
                            if (!rootNode.has(FIELD_ORDER_ID) || !rootNode.has(FIELD_GATEWAY_ORDER_ID)) {
                                log.info("Ignoring unrecognized event payload: {}", payload);
                                return null;
                            }
                            String gatewayOrderId = rootNode.get(FIELD_GATEWAY_ORDER_ID).asText();
                            String internalOrderId = rootNode.get(FIELD_ORDER_ID).asText();
                            if (rootNode.has(FIELD_EVENT_TYPE) && com.fooddelivery.common.constants.EventType.PAYMENT_REFUNDED.name().equals(rootNode.get(FIELD_EVENT_TYPE).asText())) {
                                log.info("Processing PAYMENT_REFUNDED event for order: {}", internalOrderId);
                                com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
                                if (intent != null) {
                                    intent.setStatus(PaymentIntentStatus.REFUNDED);
                                    paymentIntentRepository.save(intent);
                                    Order order = orderRepository.findById(intent.getInternalOrderId()).orElse(null);
                                    if (order != null) {
                                        order.setPaymentStatus(PaymentIntentStatus.REFUNDED);
                                        java.math.BigDecimal currentRefunded = order.getRefundedAmount() != null ? order.getRefundedAmount() : java.math.BigDecimal.ZERO;
                                        java.math.BigDecimal remainingToRefund = order.getTotalAmount().subtract(currentRefunded);
                                        if (remainingToRefund.compareTo(java.math.BigDecimal.ZERO) > 0) {
                                            order.setRefundedAmount(order.getTotalAmount());
                                            orderRepository.save(order);
                                            UUID refundTransferId = UUID.nameUUIDFromBytes((REFUND_TX_PREFIX + order.getId() + "_FULL").getBytes());
                                            orderActionService.recordLedgerTransaction(refundTransferId, PLATFORM_ACCOUNT_ID, AccountType.PLATFORM, order.getCustomerId(), AccountType.CUSTOMER, remainingToRefund, com.fooddelivery.common.enums.ChargeCategory.REFUND);
                                            try {
                                                Map<String, Object> notifPayload = new HashMap<>();
                                                notifPayload.put("orderId", order.getId().toString());
                                                notifPayload.put("customerId", order.getCustomerId().toString());
                                                notifPayload.put("refundedAmount", remainingToRefund);
                                                OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.NOTIFICATION).aggregateId(order.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.NOTIFICATION_REQUEST).payload(objectMapper.writeValueAsString(Map.of("template", "ORDER_REFUNDED", "data", notifPayload))).createdAt(LocalDateTime.now()).build();
                                                outboxEventRepository.save(outboxEvent);
                                            } catch (Exception e) {
                                                log.error("Failed to publish ORDER_REFUNDED notification", e);
                                            }

                                            if (rootNode.has("refundDestination") && "WALLET".equals(rootNode.get("refundDestination").asText())) {
                                                try {
                                                    com.fasterxml.jackson.databind.node.ObjectNode walletPayload = objectMapper.createObjectNode();
                                                    walletPayload.put("entityId", order.getCustomerId().toString());
                                                    walletPayload.put("entityType", "CUSTOMER");
                                                    walletPayload.put("amount", remainingToRefund.toString());
                                                    walletPayload.put("referenceId", "REFUND_" + order.getId());
                                                    walletPayload.put("description", "Wallet refund for order " + order.getId());
                                                    
                                                    com.fasterxml.jackson.databind.node.ObjectNode walletEvent = objectMapper.createObjectNode();
                                                    walletEvent.put("eventType", "REFUND_GENERATED");
                                                    walletEvent.set("payload", walletPayload);
                                                    
                                                    OutboxEventEntity walletOutbox = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.WALLET).aggregateId(order.getCustomerId().toString()).eventType(com.fooddelivery.common.constants.EventType.valueOf("REFUND_GENERATED")).payload(objectMapper.writeValueAsString(walletEvent)).createdAt(LocalDateTime.now()).build();
                                                    outboxEventRepository.save(walletOutbox);
                                                } catch (Exception e) {
                                                    log.error("Failed to publish REFUND_GENERATED event", e);
                                                }
                                            }
                                        }
                                    }
                                }
                                return null;
                            }
                            if (rootNode.has(FIELD_EVENT_TYPE) && com.fooddelivery.common.constants.EventType.PAYMENT_PARTIALLY_REFUNDED.name().equals(rootNode.get(FIELD_EVENT_TYPE).asText())) {
                                log.info("Processing PAYMENT_PARTIALLY_REFUNDED event for order: {}", internalOrderId);
                                com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
                                if (intent != null) {
                                    intent.setStatus(PaymentIntentStatus.PARTIALLY_REFUNDED);
                                    paymentIntentRepository.save(intent);
                                    Order order = orderRepository.findById(intent.getInternalOrderId()).orElse(null);
                                    if (order != null) {
                                        order.setPaymentStatus(PaymentIntentStatus.PARTIALLY_REFUNDED);
                                        java.math.BigDecimal partialAmount = rootNode.has("amountRefunded") ? new java.math.BigDecimal(rootNode.get("amountRefunded").asText()) : order.getTotalAmount();
                                        java.math.BigDecimal currentRefunded = order.getRefundedAmount() != null ? order.getRefundedAmount() : java.math.BigDecimal.ZERO;
                                        order.setRefundedAmount(currentRefunded.add(partialAmount));
                                        orderRepository.save(order);
                                        String uniqueSuffix = resolvedEventId != null ? resolvedEventId : String.valueOf(System.currentTimeMillis());
                                        UUID refundTransferId = UUID.nameUUIDFromBytes((REFUND_TX_PREFIX + "PARTIAL_" + order.getId() + "_" + uniqueSuffix).getBytes());
                                        orderActionService.recordLedgerTransaction(refundTransferId, PLATFORM_ACCOUNT_ID, AccountType.PLATFORM, order.getCustomerId(), AccountType.CUSTOMER, partialAmount, com.fooddelivery.common.enums.ChargeCategory.REFUND);
                                        try {
                                            Map<String, Object> notifPayload = new HashMap<>();
                                            notifPayload.put("orderId", order.getId().toString());
                                            notifPayload.put("customerId", order.getCustomerId().toString());
                                            notifPayload.put("refundedAmount", partialAmount);
                                            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.NOTIFICATION).aggregateId(order.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.NOTIFICATION_REQUEST).payload(objectMapper.writeValueAsString(Map.of("template", "ORDER_PARTIALLY_REFUNDED", "data", notifPayload))).createdAt(LocalDateTime.now()).build();
                                            outboxEventRepository.save(outboxEvent);
                                        } catch (Exception e) {
                                            log.error("Failed to publish ORDER_PARTIALLY_REFUNDED notification", e);
                                        }

                                        if (rootNode.has("refundDestination") && "WALLET".equals(rootNode.get("refundDestination").asText())) {
                                            try {
                                                com.fasterxml.jackson.databind.node.ObjectNode walletPayload = objectMapper.createObjectNode();
                                                walletPayload.put("entityId", order.getCustomerId().toString());
                                                walletPayload.put("entityType", "CUSTOMER");
                                                walletPayload.put("amount", partialAmount.toString());
                                                walletPayload.put("referenceId", "REFUND_PARTIAL_" + order.getId() + "_" + uniqueSuffix);
                                                walletPayload.put("description", "Partial wallet refund for order " + order.getId());
                                                
                                                com.fasterxml.jackson.databind.node.ObjectNode walletEvent = objectMapper.createObjectNode();
                                                walletEvent.put("eventType", "REFUND_GENERATED");
                                                walletEvent.set("payload", walletPayload);
                                                
                                                OutboxEventEntity walletOutbox = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.WALLET).aggregateId(order.getCustomerId().toString()).eventType(com.fooddelivery.common.constants.EventType.valueOf("REFUND_GENERATED")).payload(objectMapper.writeValueAsString(walletEvent)).createdAt(LocalDateTime.now()).build();
                                                outboxEventRepository.save(walletOutbox);
                                            } catch (Exception e) {
                                                log.error("Failed to publish REFUND_GENERATED event", e);
                                            }
                                        }
                                    }
                                }
                                return null;
                            }
                            boolean isFailure = rootNode.has(FIELD_FAILURE_REASON) || (rootNode.has(FIELD_EVENT_TYPE) && com.fooddelivery.common.constants.EventType.PAYMENT_FAILED.name().equals(rootNode.get(FIELD_EVENT_TYPE).asText()));
                            log.info("Payment event for gateway order {}. Finding internal order {}. IsFailure: {}", gatewayOrderId, internalOrderId, isFailure);
                            com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
                            if (intent == null) {
                                log.warn("PaymentIntent for gateway order {} not found", gatewayOrderId);
                                return null;
                            }
                            UUID orderUUID = intent.getInternalOrderId();
                            Order order = orderRepository.findById(orderUUID).orElse(null);
                            if (order == null) {
                                log.warn("Order {} not found, skipping status update", orderUUID);
                                return null;
                            }
                            com.fooddelivery.order.service.state.OrderContext context = new com.fooddelivery.order.service.state.OrderContext(order, rootNode, orderActionService);
                            com.fooddelivery.order.service.state.OrderState state = com.fooddelivery.order.service.state.OrderStateFactory.getState(order.getStatus());
                            try {
                                if (isFailure) {
                                    state.handlePaymentFailure(context);
                                } else {
                                    state.handlePaymentSuccess(context);
                                }
                            } catch (com.fooddelivery.common.exception.IllegalStateTransitionException e) {
                                log.error("ILLEGAL_STATE_TRANSITION: {}", e.getMessage());
                            }
                            if (context.isRequiresRefund()) {
                                return order;
                            }
                            return null;
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to process payment event", e);
                        }
                    });
                    if (orderToRefund != null) {
                        processRefund(orderToRefund);
                    }
                    success = true;
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                    log.warn("Optimistic locking failure in handlePaymentEvents, delegating to Kafka retry.");
                    throw e;
                } catch (Exception e) {
                    log.error("Error processing payment event payload", e);
                    throw new RuntimeException("Failed to process payment event", e);
                }
            }
        } catch (Exception e) {
            if (isNew && resolvedEventId != null) {
                redisTemplate.delete("processed_event:" + resolvedEventId);
            }
            throw e;
        }
    }

    // Listens to Kafka 'order-events' topic for ORDER_ACCEPTED
    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000))
    @KafkaListener(topics = KafkaConstants.TOPIC_ORDER_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY)
    public void handleOrderEvents(String payload, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        log.info("OrderSagaOrchestrator received event: {}", payload);
        String extractedEventId = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
        final String resolvedEventId;
        if (extractedEventId == null) {
            resolvedEventId = UUID.nameUUIDFromBytes(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            log.warn("eventId header missing in handleOrderEvents. Using deterministic payload hash as eventId: {}", resolvedEventId);
        } else {
            resolvedEventId = extractedEventId;
        }
        boolean isNew = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent("processed_event:" + resolvedEventId, "1", java.time.Duration.ofDays(7)));
        if (!isNew) {
            log.info("Duplicate event ignored: {}", resolvedEventId);
            return;
        }
        try {
            int retries = 0;
            boolean success = false;
            while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
                try {
                    Order orderToRefund = transactionTemplate.execute(status -> {
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
                                // Issue sync event to correct the offending participant
                                orderActionService.emitOrderStatusSyncEvent(order.getId(), order.getStatus());
                            }
                            if (context.isRequiresRefund()) {
                                return order;
                            }
                            return null;
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to process order event inner", e);
                        }
                    });
                    if (orderToRefund != null) {
                        processRefund(orderToRefund);
                    }
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
            if (isNew && resolvedEventId != null) {
                redisTemplate.delete("processed_event:" + resolvedEventId);
            }
            throw e;
        }
    }

    @Transactional
    public void publishDelayApprovalEvent(Order order, boolean approved, String reason) {
        try {
            String eventType = approved ? EventType.ORDER_DELAY_APPROVED.name() : EventType.ORDER_DELAY_REJECTED.name();
            com.fasterxml.jackson.databind.node.ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("eventType", eventType);
            payloadNode.put("orderId", order.getId().toString());
            payloadNode.put("restaurantId", order.getRestaurantId().toString());
            if (reason != null && !reason.isEmpty()) {
                payloadNode.put("reason", reason);
            }
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(order.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.valueOf(eventType)).payload(objectMapper.writeValueAsString(payloadNode)).createdAt(LocalDateTime.now()).build();
            log.info("Triggering event: {} for order: {}", eventType, order.getId());
            outboxEventRepository.save(outboxEvent);
            log.info("Saved outbox event {} for Order {}", eventType, order.getId());
        } catch (Exception e) {
            log.error("Failed to save delay approval event", e);
            throw new OrderProcessingException("Failed to process delay approval", e);
        }
    }

    public void cancelOrderLocally(Order order, String reason) {
        Order orderToRefund = transactionTemplate.execute(status -> {
            Order dbOrder = orderRepository.findById(order.getId()).orElse(order);
            com.fooddelivery.order.service.state.OrderContext context = new com.fooddelivery.order.service.state.OrderContext(dbOrder, objectMapper.createObjectNode(), orderActionService);
            com.fooddelivery.order.service.state.OrderState state = com.fooddelivery.order.service.state.OrderStateFactory.getState(dbOrder.getStatus());
            try {
                state.cancelByCustomer(context, reason);
            } catch (com.fooddelivery.common.exception.IllegalStateTransitionException e) {
                log.error("ILLEGAL_STATE_TRANSITION: {}", e.getMessage());
                throw new IllegalStateException(e.getMessage());
            }
            if (context.isRequiresRefund()) {
                return dbOrder;
            }
            return null;
        });
        if (orderToRefund != null) {
            processRefund(orderToRefund);
        }
    }

    @io.micrometer.core.annotation.Timed(value = "order.saga.refund.process", description = "Time taken to process full refund")
    public void processRefund(Order order) {
        processRefund(order, com.fooddelivery.common.enums.RefundDestination.GATEWAY);
    }

    public void processRefund(Order order, com.fooddelivery.common.enums.RefundDestination refundDestination) {
        log.info("Processing refund for Order {} to {}", order.getId(), refundDestination);
        
        try {
            transactionTemplate.executeWithoutResult(status -> {
                com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByInternalOrderIdForUpdate(order.getId())
                    .orElseThrow(() -> new IllegalStateException("PaymentIntent not found for internalOrderId " + order.getId() + ". Cannot process refund."));
                    
                java.math.BigDecimal currentRefunded = order.getRefundedAmount() != null ? order.getRefundedAmount() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal remainingRefundable = order.getTotalAmount().subtract(currentRefunded);
                
                if (remainingRefundable.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    log.info("Order {} is already fully refunded. Skipping.", order.getId());
                    return;
                }
                
                if (intent.getStatus() == PaymentIntentStatus.SUCCESS || intent.getStatus() == PaymentIntentStatus.CAPTURED || intent.getStatus() == PaymentIntentStatus.PARTIALLY_REFUNDED || intent.getStatus() == PaymentIntentStatus.REFUND_FAILED) {
                    try {
                        Map<String, Object> payloadMap = new HashMap<>();
                        payloadMap.put("intentId", intent.getId().toString());
                        payloadMap.put("gatewayOrderId", intent.getGatewayOrderId());
                        payloadMap.put("amountInInr", remainingRefundable);
                        payloadMap.put("gatewayName", intent.getGatewayName());
                        payloadMap.put("orderId", order.getId().toString());
                        payloadMap.put("refundDestination", refundDestination != null ? refundDestination.name() : "GATEWAY");
                        String payloadStr = objectMapper.writeValueAsString(payloadMap);
                        OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.PAYMENT).aggregateId(order.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.PAYMENT_REFUND_REQUESTED).payload(payloadStr).createdAt(LocalDateTime.now()).build();
                        outboxEventRepository.save(outboxEvent);
                        
                        // Handle Post-Delivery Reversals
                        Order latestOrder = orderRepository.findById(order.getId()).orElse(order);
                        if (latestOrder.getDeliveryStatus() == com.fooddelivery.common.enums.DeliveryStatus.DELIVERED) {
                            UUID reversalId = UUID.randomUUID();
                            Map<String, Object> reversalPayload = new HashMap<>();
                            reversalPayload.put("reversalId", reversalId.toString());
                            reversalPayload.put("orderId", order.getId().toString());
                            reversalPayload.put("amount", remainingRefundable);
                            String reversalStr = objectMapper.writeValueAsString(reversalPayload);
                            OutboxEventEntity reversalEvent = OutboxEventEntity.builder()
                                .id(reversalId)
                                .aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER)
                                .aggregateId(order.getId().toString())
                                .eventType(com.fooddelivery.common.constants.EventType.valueOf("LEDGER_REVERSAL_REQUEST")) // Custom event type
                                .payload(reversalStr)
                                .createdAt(LocalDateTime.now())
                                .build();
                            outboxEventRepository.save(reversalEvent);
                            log.info("Emitted LEDGER_REVERSAL_REQUEST for delivered order {}", order.getId());
                        }

                        latestOrder.setPaymentStatus(PaymentIntentStatus.REFUND_PENDING);
                        orderRepository.save(latestOrder);
                        
                        intent.setStatus(PaymentIntentStatus.REFUND_PENDING);
                        paymentIntentRepository.save(intent);
                    } catch (Exception e) {
                        log.error("Failed to enqueue payment refund event for order {}", order.getId(), e);
                        markRefundFailedWithRetry(intent);
                        throw new RuntimeException(e); // Rollback tx
                    }
                } else {
                    log.warn("Cannot refund PaymentIntent {} in status {}", intent.getId(), intent.getStatus());
                }
            });
        } catch (Exception e) {
            log.error("Failed to process refund for order {}", order.getId(), e);
        }
    }

    @io.micrometer.core.annotation.Timed(value = "order.saga.refund.process.partial", description = "Time taken to process partial refund")
    public void processPartialRefund(Order order, java.math.BigDecimal partialAmount) {
        processPartialRefund(order, partialAmount, com.fooddelivery.common.enums.RefundDestination.GATEWAY);
    }

    public void processPartialRefund(Order order, java.math.BigDecimal partialAmount, com.fooddelivery.common.enums.RefundDestination refundDestination) {
        log.info("Processing partial refund for Order {} to {}", order.getId(), refundDestination);
        if (partialAmount == null || partialAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Partial refund amount must be greater than zero");
        }
        
        try {
            transactionTemplate.executeWithoutResult(status -> {
                com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByInternalOrderIdForUpdate(order.getId())
                    .orElseThrow(() -> new IllegalStateException("PaymentIntent not found for internalOrderId " + order.getId() + ". Cannot process partial refund."));

                java.math.BigDecimal currentRefunded = order.getRefundedAmount() != null ? order.getRefundedAmount() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal remainingRefundable = order.getTotalAmount().subtract(currentRefunded);
                if (partialAmount.compareTo(remainingRefundable) > 0) {
                    throw new IllegalArgumentException("Partial refund amount " + partialAmount + " exceeds remaining refundable balance " + remainingRefundable);
                }

                if (intent.getStatus() == PaymentIntentStatus.SUCCESS || intent.getStatus() == PaymentIntentStatus.CAPTURED || intent.getStatus() == PaymentIntentStatus.PARTIALLY_REFUNDED || intent.getStatus() == PaymentIntentStatus.REFUND_FAILED) {
                    try {
                        Map<String, Object> payloadMap = new HashMap<>();
                        payloadMap.put("intentId", intent.getId().toString());
                        payloadMap.put("gatewayOrderId", intent.getGatewayOrderId());
                        payloadMap.put("amountInInr", partialAmount);
                        payloadMap.put("gatewayName", intent.getGatewayName());
                        payloadMap.put("orderId", order.getId().toString());
                        payloadMap.put("refundDestination", refundDestination != null ? refundDestination.name() : "GATEWAY");
                        String payloadStr = objectMapper.writeValueAsString(payloadMap);
                        OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.PAYMENT).aggregateId(order.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.PAYMENT_REFUND_REQUESTED).payload(payloadStr).createdAt(LocalDateTime.now()).build();
                        outboxEventRepository.save(outboxEvent);

                        // Handle Post-Delivery Reversals
                        Order latestOrder = orderRepository.findById(order.getId()).orElse(order);
                        if (latestOrder.getDeliveryStatus() == com.fooddelivery.common.enums.DeliveryStatus.DELIVERED) {
                            UUID reversalId = UUID.randomUUID();
                            Map<String, Object> reversalPayload = new HashMap<>();
                            reversalPayload.put("reversalId", reversalId.toString());
                            reversalPayload.put("orderId", order.getId().toString());
                            reversalPayload.put("amount", partialAmount); // Reversing only the partial amount
                            String reversalStr = objectMapper.writeValueAsString(reversalPayload);
                            OutboxEventEntity reversalEvent = OutboxEventEntity.builder()
                                .id(reversalId)
                                .aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER)
                                .aggregateId(order.getId().toString())
                                .eventType(com.fooddelivery.common.constants.EventType.valueOf("LEDGER_REVERSAL_REQUEST")) 
                                .payload(reversalStr)
                                .createdAt(LocalDateTime.now())
                                .build();
                            outboxEventRepository.save(reversalEvent);
                            log.info("Emitted partial LEDGER_REVERSAL_REQUEST for delivered order {}", order.getId());
                        }

                        latestOrder.setPaymentStatus(PaymentIntentStatus.REFUND_PENDING);
                        orderRepository.save(latestOrder);
                        
                        intent.setStatus(PaymentIntentStatus.REFUND_PENDING);
                        paymentIntentRepository.save(intent);
                    } catch (Exception e) {
                        log.error("Failed to enqueue payment refund event for order {}", order.getId(), e);
                        markRefundFailedWithRetry(intent);
                        throw new RuntimeException(e); // Rollback tx
                    }
                } else {
                    log.warn("Cannot refund PaymentIntent {} in status {}", intent.getId(), intent.getStatus());
                }
            });
        } catch (Exception e) {
            log.error("Failed to process partial refund for order {}", order.getId(), e);
        }
    }

    private void markRefundFailedWithRetry(com.fooddelivery.order.entity.PaymentIntent intent) {
        int retries = 0;
        boolean success = false;
        while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    com.fooddelivery.order.entity.PaymentIntent latestIntent = paymentIntentRepository.findById(intent.getId()).orElse(intent);
                    latestIntent.setStatus(PaymentIntentStatus.REFUND_FAILED);
                    paymentIntentRepository.save(latestIntent);
                });
                success = true;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                retries++;
                if (retries >= AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
                    log.error("CRITICAL: Failed to mark PaymentIntent {} as REFUND_FAILED after {} retries", intent.getId(), AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES, e);
                    return; // Prevent bubbling up and failing the parent Kafka listener
                }
                log.warn("Optimistic locking failure while marking REFUND_FAILED for intent {}. Retrying {}/{}", intent.getId(), retries, AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES);
                try {
                    Thread.sleep((long) (Math.pow(2, retries) * 100));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                log.error("Failed to mark PaymentIntent {} as REFUND_FAILED", intent.getId(), e);
                return; // Prevent bubbling up
            }
        }
    }


    public static class WebhookPayloadDTO {
        private String event;
        private PayloadData payload;

        @java.lang.SuppressWarnings("all")
        public WebhookPayloadDTO() {
        }

        @java.lang.SuppressWarnings("all")
        public String getEvent() {
            return this.event;
        }

        @java.lang.SuppressWarnings("all")
        public PayloadData getPayload() {
            return this.payload;
        }

        @java.lang.SuppressWarnings("all")
        public void setEvent(final String event) {
            this.event = event;
        }

        @java.lang.SuppressWarnings("all")
        public void setPayload(final PayloadData payload) {
            this.payload = payload;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public boolean equals(final java.lang.Object o) {
            if (o == this) return true;
            if (!(o instanceof OrderSagaOrchestrator.WebhookPayloadDTO)) return false;
            final OrderSagaOrchestrator.WebhookPayloadDTO other = (OrderSagaOrchestrator.WebhookPayloadDTO) o;
            if (!other.canEqual((java.lang.Object) this)) return false;
            final java.lang.Object this$event = this.getEvent();
            final java.lang.Object other$event = other.getEvent();
            if (this$event == null ? other$event != null : !this$event.equals(other$event)) return false;
            final java.lang.Object this$payload = this.getPayload();
            final java.lang.Object other$payload = other.getPayload();
            if (this$payload == null ? other$payload != null : !this$payload.equals(other$payload)) return false;
            return true;
        }

        @java.lang.SuppressWarnings("all")
        protected boolean canEqual(final java.lang.Object other) {
            return other instanceof OrderSagaOrchestrator.WebhookPayloadDTO;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final java.lang.Object $event = this.getEvent();
            result = result * PRIME + ($event == null ? 43 : $event.hashCode());
            final java.lang.Object $payload = this.getPayload();
            result = result * PRIME + ($payload == null ? 43 : $payload.hashCode());
            return result;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "OrderSagaOrchestrator.WebhookPayloadDTO(event=" + this.getEvent() + ", payload=" + this.getPayload() + ")";
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000) // Runs every minute
    public void sweepStuckOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        
        java.util.List<Order> stuckCreated = orderRepository.findByStatusAndCreatedAtBefore(com.fooddelivery.common.enums.OrderStatus.CREATED, threshold);
        for (Order order : stuckCreated) {
            log.warn("Sweeper: Cancelling stuck order {} (in CREATED state > 15m)", order.getId());
            try {
                cancelOrderLocally(order, "Order stuck in CREATED state");
            } catch (Exception e) {
                log.error("Failed to cancel stuck CREATED order " + order.getId(), e);
            }
        }
        
        java.util.List<Order> stuckPending = orderRepository.findByStatusAndCreatedAtBefore(com.fooddelivery.common.enums.OrderStatus.PENDING_ACCEPTANCE, threshold);
        for (Order order : stuckPending) {
            log.warn("Sweeper: Cancelling stuck order {} (in PENDING_ACCEPTANCE state > 15m)", order.getId());
            try {
                cancelOrderLocally(order, "Order stuck in PENDING_ACCEPTANCE state");
            } catch (Exception e) {
                log.error("Failed to cancel stuck PENDING_ACCEPTANCE order " + order.getId(), e);
            }
        }
    }


    public static class PayloadData {
        private PaymentData payment;

        @java.lang.SuppressWarnings("all")
        public PayloadData() {
        }

        @java.lang.SuppressWarnings("all")
        public PaymentData getPayment() {
            return this.payment;
        }

        @java.lang.SuppressWarnings("all")
        public void setPayment(final PaymentData payment) {
            this.payment = payment;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public boolean equals(final java.lang.Object o) {
            if (o == this) return true;
            if (!(o instanceof OrderSagaOrchestrator.PayloadData)) return false;
            final OrderSagaOrchestrator.PayloadData other = (OrderSagaOrchestrator.PayloadData) o;
            if (!other.canEqual((java.lang.Object) this)) return false;
            final java.lang.Object this$payment = this.getPayment();
            final java.lang.Object other$payment = other.getPayment();
            if (this$payment == null ? other$payment != null : !this$payment.equals(other$payment)) return false;
            return true;
        }

        @java.lang.SuppressWarnings("all")
        protected boolean canEqual(final java.lang.Object other) {
            return other instanceof OrderSagaOrchestrator.PayloadData;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final java.lang.Object $payment = this.getPayment();
            result = result * PRIME + ($payment == null ? 43 : $payment.hashCode());
            return result;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "OrderSagaOrchestrator.PayloadData(payment=" + this.getPayment() + ")";
        }
    }


    public static class PaymentData {
        private PaymentEntity entity;

        @java.lang.SuppressWarnings("all")
        public PaymentData() {
        }

        @java.lang.SuppressWarnings("all")
        public PaymentEntity getEntity() {
            return this.entity;
        }

        @java.lang.SuppressWarnings("all")
        public void setEntity(final PaymentEntity entity) {
            this.entity = entity;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public boolean equals(final java.lang.Object o) {
            if (o == this) return true;
            if (!(o instanceof OrderSagaOrchestrator.PaymentData)) return false;
            final OrderSagaOrchestrator.PaymentData other = (OrderSagaOrchestrator.PaymentData) o;
            if (!other.canEqual((java.lang.Object) this)) return false;
            final java.lang.Object this$entity = this.getEntity();
            final java.lang.Object other$entity = other.getEntity();
            if (this$entity == null ? other$entity != null : !this$entity.equals(other$entity)) return false;
            return true;
        }

        @java.lang.SuppressWarnings("all")
        protected boolean canEqual(final java.lang.Object other) {
            return other instanceof OrderSagaOrchestrator.PaymentData;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final java.lang.Object $entity = this.getEntity();
            result = result * PRIME + ($entity == null ? 43 : $entity.hashCode());
            return result;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "OrderSagaOrchestrator.PaymentData(entity=" + this.getEntity() + ")";
        }
    }


    public static class PaymentEntity {
        @com.fasterxml.jackson.annotation.JsonProperty("order_id")
        private String orderId;
        private String status;
        private double amount;

        @java.lang.SuppressWarnings("all")
        public PaymentEntity() {
        }

        @java.lang.SuppressWarnings("all")
        public String getOrderId() {
            return this.orderId;
        }

        @java.lang.SuppressWarnings("all")
        public String getStatus() {
            return this.status;
        }

        @java.lang.SuppressWarnings("all")
        public double getAmount() {
            return this.amount;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("order_id")
        @java.lang.SuppressWarnings("all")
        public void setOrderId(final String orderId) {
            this.orderId = orderId;
        }

        @java.lang.SuppressWarnings("all")
        public void setStatus(final String status) {
            this.status = status;
        }

        @java.lang.SuppressWarnings("all")
        public void setAmount(final double amount) {
            this.amount = amount;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public boolean equals(final java.lang.Object o) {
            if (o == this) return true;
            if (!(o instanceof OrderSagaOrchestrator.PaymentEntity)) return false;
            final OrderSagaOrchestrator.PaymentEntity other = (OrderSagaOrchestrator.PaymentEntity) o;
            if (!other.canEqual((java.lang.Object) this)) return false;
            if (java.lang.Double.compare(this.getAmount(), other.getAmount()) != 0) return false;
            final java.lang.Object this$orderId = this.getOrderId();
            final java.lang.Object other$orderId = other.getOrderId();
            if (this$orderId == null ? other$orderId != null : !this$orderId.equals(other$orderId)) return false;
            final java.lang.Object this$status = this.getStatus();
            final java.lang.Object other$status = other.getStatus();
            if (this$status == null ? other$status != null : !this$status.equals(other$status)) return false;
            return true;
        }

        @java.lang.SuppressWarnings("all")
        protected boolean canEqual(final java.lang.Object other) {
            return other instanceof OrderSagaOrchestrator.PaymentEntity;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final long $amount = java.lang.Double.doubleToLongBits(this.getAmount());
            result = result * PRIME + (int) ($amount >>> 32 ^ $amount);
            final java.lang.Object $orderId = this.getOrderId();
            result = result * PRIME + ($orderId == null ? 43 : $orderId.hashCode());
            final java.lang.Object $status = this.getStatus();
            result = result * PRIME + ($status == null ? 43 : $status.hashCode());
            return result;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "OrderSagaOrchestrator.PaymentEntity(orderId=" + this.getOrderId() + ", status=" + this.getStatus() + ", amount=" + this.getAmount() + ")";
        }
    }

    private void sendNotification(String orderId, UUID customerId, String templateCode) {
        try {
            com.fooddelivery.common.event.NotificationRequestEvent notificationEvent = com.fooddelivery.common.event.NotificationRequestEvent.builder().userId(customerId).channel(com.fooddelivery.common.enums.ChannelType.PUSH).eventName(templateCode).templateParams(java.util.List.of(orderId)).build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.NOTIFICATION).aggregateId(customerId.toString()).eventType(EventType.NOTIFICATION_REQUEST).payload(objectMapper.writeValueAsString(notificationEvent)).createdAt(LocalDateTime.now()).build();
            log.info("Triggering event: {} for customer: {}", EventType.NOTIFICATION_REQUEST.name(), customerId);
            outboxEventRepository.save(outboxEvent);
            log.info("Saved notification request to outbox for order {} to customer {}", orderId, customerId);
        } catch (Exception e) {
            log.error("Failed to save notification request to outbox", e);
            throw new RuntimeException("Failed to save notification", e);
        }
    }

    private boolean isTerminalState(OrderStatus status) {
        return status == OrderStatus.HANDED_OVER || status == OrderStatus.CANCELLED || status == OrderStatus.CANCELLED_BY_RESTAURANT;
    }

    @DltHandler
    public void processDeadLetterTopic(@Payload(required = false) String payload, @org.springframework.messaging.handler.annotation.Header(name = org.springframework.kafka.support.KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        log.error("Terminal failure for event in Saga. Payload: {}. Moving to manual intervention queue. Exception: {}", payload, exceptionMessage);
    }

    @java.lang.SuppressWarnings("all")
    public OrderSagaOrchestrator(final IOrderRepository orderRepository, final OutboxEventRepository outboxEventRepository, final IPaymentIntentRepository paymentIntentRepository, final ObjectMapper objectMapper, final KafkaTemplate<String, String> kafkaTemplate, final com.fooddelivery.customer.client.PaymentClient paymentClient, final org.springframework.transaction.support.TransactionTemplate transactionTemplate, final com.fooddelivery.order.service.state.OrderActionService orderActionService, final StringRedisTemplate redisTemplate) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.paymentClient = paymentClient;
        this.transactionTemplate = transactionTemplate;
        this.orderActionService = orderActionService;
        this.redisTemplate = redisTemplate;
    }
}
