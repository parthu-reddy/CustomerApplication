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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {
    
    private final IOrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IPaymentIntentRepository paymentIntentRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DoubleEntryLedgerService ledgerService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private static final java.util.Map<String, java.util.function.BiConsumer<com.fooddelivery.order.service.state.OrderState, com.fooddelivery.order.service.state.OrderContext>> EVENT_HANDLERS = new java.util.HashMap<>();
    static {
        EVENT_HANDLERS.put(EventType.ORDER_DELIVERED.name(), com.fooddelivery.order.service.state.OrderState::handleOrderDelivered);
        EVENT_HANDLERS.put(EventType.ORDER_CANCELLED_BY_RESTAURANT.name(), com.fooddelivery.order.service.state.OrderState::handleOrderCancelledByRestaurant);
        EVENT_HANDLERS.put(EventType.ORDER_REJECTED.name(), com.fooddelivery.order.service.state.OrderState::handleOrderCancelledByRestaurant);
        EVENT_HANDLERS.put(EventType.DRIVER_ASSIGNED.name(), com.fooddelivery.order.service.state.OrderState::handleDriverAssigned);
        EVENT_HANDLERS.put(EventType.DISPATCH_FAILED.name(), com.fooddelivery.order.service.state.OrderState::handleDispatchFailed);
        EVENT_HANDLERS.put(EventType.DELIVERY_FAILED.name(), com.fooddelivery.order.service.state.OrderState::handleDeliveryFailed);
        EVENT_HANDLERS.put(EventType.ORDER_DELAY_APPROVAL_REQUESTED.name(), com.fooddelivery.order.service.state.OrderState::handleDelayApprovalRequested);
        EVENT_HANDLERS.put(EventType.ORDER_DELAY_REJECTED.name(), com.fooddelivery.order.service.state.OrderState::handleDelayRejected);
        EVENT_HANDLERS.put(EventType.ORDER_ACCEPTED.name(), com.fooddelivery.order.service.state.OrderState::handleOrderAccepted);
        EVENT_HANDLERS.put(EventType.ORDER_PREPARING.name(), com.fooddelivery.order.service.state.OrderState::handleOrderPreparing);
        EVENT_HANDLERS.put(EventType.ORDER_READY.name(), com.fooddelivery.order.service.state.OrderState::handleOrderReady);
        EVENT_HANDLERS.put(EventType.ORDER_AT_RESTAURANT.name(), com.fooddelivery.order.service.state.OrderState::handleDriverAtRestaurant);

    }
    private final com.fooddelivery.customer.client.PaymentClient paymentClient;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final com.fooddelivery.order.service.state.OrderActionService orderActionService;

    // We assume the system account ID for the platform is a fixed UUID for this prototype
    private static final UUID PLATFORM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Transactional
    public Order startOrderSaga(Order order) {
        // Generate 6-digit pickup OTP
        String otp = String.format("%06d", new java.util.Random().nextInt(1000000));
        order.setPickupOtp(otp);
        
        // Generate 6-digit delivery OTP
        String deliveryOtp = String.format("%06d", new java.util.Random().nextInt(1000000));
        order.setOtp(deliveryOtp);
        
        Order savedOrder = orderRepository.save(order);
        
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .customerId(savedOrder.getCustomerId())
                .restaurantId(savedOrder.getRestaurantId())
                .totalAmount(savedOrder.getTotalAmount())
                .deliveryLat(savedOrder.getDeliveryLat())
                .deliveryLng(savedOrder.getDeliveryLng())
                .deliveryAddress(savedOrder.getDeliveryAddress())
                .pickupOtp(savedOrder.getPickupOtp())
                .deliveryOtp(savedOrder.getOtp())
                .build();
                
        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER)
                    .aggregateId(savedOrder.getId().toString())
                    .eventType(EventType.ORDER_CREATED)
                    .payload(objectMapper.writeValueAsString(event))
                    .createdAt(LocalDateTime.now())
                    .build();
                    
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
        
        OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .eventType(event.getEventType())
                .payload(event.getPayload())
                .createdAt(LocalDateTime.now())
                .build();
                
        log.info("Triggering event: {} for aggregate: {}", event.getEventType(), event.getAggregateId());
        outboxEventRepository.save(outboxEvent);
        log.info("Order state and outbox event saved for Order ID: {}", order.getId());
    }

    // Listens to Kafka 'payment-events' topic for events published by external Payment Service
    @KafkaListener(topics = KafkaConstants.TOPIC_PAYMENT_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY)
    public void handlePaymentEvents(String payload) {
        log.info("Received Payment Event: {}", payload);
        
        int retries = 0;
        boolean success = false;
        while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
            try {
                Order orderToRefund = transactionTemplate.execute(status -> {
                    try {
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);
            
            if (!rootNode.has("orderId") || !rootNode.has("gatewayOrderId")) {
                log.info("Ignoring unrecognized event payload: {}", payload);
                return null;
            }
            
            String gatewayOrderId = rootNode.get("gatewayOrderId").asText();
            String internalOrderId = rootNode.get("orderId").asText();
            
            if (rootNode.has("eventType") && com.fooddelivery.common.constants.EventType.PAYMENT_REFUNDED.name().equals(rootNode.get("eventType").asText())) {
                log.info("Ignoring PAYMENT_REFUNDED event for order: {}", internalOrderId);
                return null;
            }
            
            boolean isFailure = rootNode.has("failureReason") || 
                    (rootNode.has("eventType") && com.fooddelivery.common.constants.EventType.PAYMENT_FAILED.name().equals(rootNode.get("eventType").asText()));
            
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
                success = true;
                if (orderToRefund != null) {
                    processRefund(orderToRefund);
                }
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                retries++;
                if (retries >= AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
                    log.error("Failed to process payment event after {} retries due to optimistic locking", AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES, e);
                    throw e;
                }
                log.warn("Optimistic locking failure in handlePaymentEvents. Retrying {}/{}", retries, AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES);
                try { Thread.sleep((long) (Math.pow(2, retries) * 100)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            } catch (Exception e) {
                log.error("Error processing payment event payload", e);
                throw new RuntimeException("Failed to process payment event", e);
            }
        }
    }

    // Listens to Kafka 'order-events' topic for ORDER_ACCEPTED
    @KafkaListener(topics = KafkaConstants.TOPIC_ORDER_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY)
    public void handleOrderEvents(String payload, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        log.info("OrderSagaOrchestrator received event: {}", payload);
        
        int retries = 0;
        boolean success = false;
        while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
            try {
                Order orderToRefund = transactionTemplate.execute(status -> {
                    try {
                        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);
                        String jsonEventType = rootNode.path("eventType").asText(null);
                        
                        String headerEventType = null;
                        Object eventTypeObj = headers.get("eventType");
                        if (eventTypeObj != null) {
                            if (eventTypeObj instanceof byte[]) {
                                headerEventType = new String((byte[]) eventTypeObj, java.nio.charset.StandardCharsets.UTF_8);
                            } else if (eventTypeObj.getClass().getName().contains("NonTrustedHeaderType")) {
                                String str = eventTypeObj.toString();
                                if (str.contains("headerValue=")) {
                                    int start = str.indexOf("\"") + 1;
                                    if (start > 0) {
                                        int end = str.indexOf("\"", start);
                                        if (end > start) {
                                            headerEventType = str.substring(start, end);
                                        } else {
                                            headerEventType = str;
                                        }
                                    } else {
                                        headerEventType = str;
                                    }
                                } else {
                                    headerEventType = str;
                                }
                            } else {
                                headerEventType = eventTypeObj.toString();
                            }
                        }
                        
                        String eventType = headerEventType != null ? headerEventType : jsonEventType;
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
                success = true;
                if (orderToRefund != null) {
                    processRefund(orderToRefund);
                }
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                retries++;
                if (retries >= AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
                    log.error("Failed to process order event after {} retries due to optimistic locking", AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES, e);
                    throw e;
                }
                log.warn("Optimistic locking failure in handleOrderEvents. Retrying {}/{}", retries, AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES);
                try { Thread.sleep((long) (Math.pow(2, retries) * 100)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            } catch (Exception e) {
                log.error("Error processing order event", e);
                throw new RuntimeException("Failed to process order event", e);
            }
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
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER)
                    .aggregateId(order.getId().toString())
                    .eventType(com.fooddelivery.common.constants.EventType.valueOf(eventType))
                    .payload(objectMapper.writeValueAsString(payloadNode))
                    .createdAt(LocalDateTime.now())
                    .build();
            log.info("Triggering event: {} for order: {}", eventType, order.getId());
            outboxEventRepository.save(outboxEvent);
            log.info("Saved outbox event {} for Order {}", eventType, order.getId());
        } catch (Exception e) {
            log.error("Failed to save delay approval event", e);
            throw new OrderProcessingException("Failed to process delay approval", e);
        }
    }

    @Transactional
    public void cancelOrderLocally(Order order, String reason) {
        com.fooddelivery.order.service.state.OrderContext context = new com.fooddelivery.order.service.state.OrderContext(order, objectMapper.createObjectNode(), orderActionService);
        com.fooddelivery.order.service.state.OrderState state = com.fooddelivery.order.service.state.OrderStateFactory.getState(order.getStatus());
        
        try {
            state.cancelByCustomer(context, reason);
        } catch (com.fooddelivery.common.exception.IllegalStateTransitionException e) {
            log.error("ILLEGAL_STATE_TRANSITION: {}", e.getMessage());
            throw new IllegalStateException(e.getMessage());
        }

        if (context.isRequiresRefund()) {
            processRefund(order);
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "60000") // Run every 1 minute
    public void checkDelayApprovalTimeouts() {
        java.time.LocalDateTime cutoffTime = java.time.LocalDateTime.now().minusMinutes(10);
        
        java.util.List<Order> delayedOrders = transactionTemplate.execute(status -> {
            return orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.AWAITING_DELAY_APPROVAL, cutoffTime);
        });
        
        if (delayedOrders == null || delayedOrders.isEmpty()) return;
        
        for (Order order : delayedOrders) {
            log.info("Order {} exceeded 10-minute delay approval timeout. Cancelling order.", order.getId());
            
            boolean eventPublished = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                Order dbOrder = orderRepository.findById(order.getId()).orElse(null);
                if (dbOrder != null && dbOrder.getStatus() == OrderStatus.AWAITING_DELAY_APPROVAL) {
                    publishDelayApprovalEvent(dbOrder, false, "Auto-cancelled: Customer did not respond to delay approval in 10 minutes");
                    return true;
                }
                return false;
            }));
            
            if (eventPublished) {
                log.info("Published ORDER_DELAY_REJECTED for order {} due to timeout.", order.getId());
            }
        }
    }

    public void processRefund(Order order) {
        log.info("Processing refund for Order {}", order.getId());
        paymentIntentRepository.findByInternalOrderId(order.getId()).ifPresent(intent -> {
            if (intent.getStatus() == PaymentIntentStatus.SUCCESS || intent.getStatus() == PaymentIntentStatus.CAPTURED || intent.getStatus() == PaymentIntentStatus.REFUND_FAILED) {
                try {
                    Map<String, Object> request = new HashMap<>();
                    request.put("gatewayOrderId", intent.getGatewayOrderId());
                    request.put("amountInInr", order.getTotalAmount());
                    request.put("reason", "Order cancelled or rejected");
                    
                    String response = null;
                    try {
                        response = paymentClient.refundOrder(intent.getGatewayName(), request);
                    } catch (Exception e) {
                        log.error("Failed to initiate refund via PaymentGatewayIntegration", e);
                    }
                    
                    if (response != null) {
                        int retries = 0;
                        boolean success = false;
                        while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
                            try {
                                transactionTemplate.executeWithoutResult(status -> {
                                    // RE-FETCH inside transaction to get latest version for optimistic locking retry
                                    com.fooddelivery.order.entity.PaymentIntent latestIntent = paymentIntentRepository.findById(intent.getId())
                                            .orElseThrow(() -> new RuntimeException("PaymentIntent not found during refund retry"));
                                    latestIntent.setStatus(PaymentIntentStatus.REFUNDED);
                                    paymentIntentRepository.save(latestIntent);
                                    
                                    Order latestOrder = orderRepository.findById(order.getId()).orElse(null);
                                    if(latestOrder != null) {
                                        latestOrder.setPaymentStatus(PaymentIntentStatus.REFUNDED);
                                        orderRepository.save(latestOrder);
                                    }
                                    
                                    UUID refundTransferId = UUID.nameUUIDFromBytes(("REFUND_" + order.getId()).getBytes());
                                    ledgerService.recordTransaction(refundTransferId, PLATFORM_ACCOUNT_ID, AccountType.PLATFORM, order.getCustomerId(), AccountType.CUSTOMER, order.getTotalAmount());
                                });
                                success = true;
                                log.info("PaymentIntent {} status updated to REFUNDED. Funds returned to Customer via Gateway.", intent.getId());
                            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                                retries++;
                                if (retries >= AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
                                    log.error("CRITICAL: Refund successful in gateway but failed in DB after {} retries due to optimistic locking for order {}", AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES, order.getId(), e);
                                    throw e;
                                }
                                log.warn("Optimistic locking failure in processRefund for order {}. Retrying {}/{}", order.getId(), retries, AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES);
                                try { Thread.sleep((long) (Math.pow(2, retries) * 100)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            }
                        }
                    } else {
                        log.error("Failed to initiate refund via PaymentGatewayIntegration.");
                        markRefundFailedWithRetry(intent);
                    }
                } catch (Exception e) {
                    log.error("Exception calling PaymentGatewayIntegration for refund on order {}. Marking as REFUND_FAILED.", order.getId(), e);
                    markRefundFailedWithRetry(intent);
                }
            } else {
                log.info("PaymentIntent for Order {} is not in a refundable state: {}", order.getId(), intent.getStatus());
            }
        });
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
                try { Thread.sleep((long) (Math.pow(2, retries) * 100)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            } catch (Exception e) {
                log.error("Failed to mark PaymentIntent {} as REFUND_FAILED", intent.getId(), e);
                return; // Prevent bubbling up
            }
        }
    }

    @lombok.Data
    public static class WebhookPayloadDTO {
        private String event;
        private PayloadData payload;
    }

    @lombok.Data
    public static class PayloadData {
        private PaymentData payment;
    }

    @lombok.Data
    public static class PaymentData {
        private PaymentEntity entity;
    }

    @lombok.Data
    public static class PaymentEntity {
        @com.fasterxml.jackson.annotation.JsonProperty("order_id")
        private String orderId;
        private String status;
        private double amount;
    }

    private void sendNotification(String orderId, UUID customerId, String templateCode) {
        try {
            com.fooddelivery.common.event.NotificationRequestEvent notificationEvent = com.fooddelivery.common.event.NotificationRequestEvent.builder()
                    .userId(customerId)
                    .channel(com.fooddelivery.common.enums.ChannelType.PUSH)
                    .eventName(templateCode)
                    .templateParams(java.util.List.of(orderId))
                    .build();
            
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(com.fooddelivery.common.constants.AggregateType.NOTIFICATION)
                    .aggregateId(customerId.toString())
                    .eventType(EventType.NOTIFICATION_REQUEST)
                    .payload(objectMapper.writeValueAsString(notificationEvent))
                    .createdAt(LocalDateTime.now())
                    .build();
            log.info("Triggering event: {} for customer: {}", EventType.NOTIFICATION_REQUEST.name(), customerId);
            outboxEventRepository.save(outboxEvent);
            
            log.info("Saved notification request to outbox for order {} to customer {}", orderId, customerId);
        } catch (Exception e) {
            log.error("Failed to save notification request to outbox", e);
            throw new RuntimeException("Failed to save notification", e);
        }
    }

    private boolean isTerminalState(OrderStatus status) {
        return status == OrderStatus.DELIVERED || 
               status == OrderStatus.CANCELLED ||
               status == OrderStatus.CANCELLED_BY_RESTAURANT ||
               status == OrderStatus.DELIVERY_FAILED;
    }
}
