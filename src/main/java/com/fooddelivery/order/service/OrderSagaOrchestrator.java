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
        EVENT_HANDLERS.put(EventType.ORDER_DELIVERED, com.fooddelivery.order.service.state.OrderState::handleOrderDelivered);
        EVENT_HANDLERS.put(EventType.ORDER_CANCELLED_BY_RESTAURANT, com.fooddelivery.order.service.state.OrderState::handleOrderCancelledByRestaurant);
        EVENT_HANDLERS.put(EventType.ORDER_REJECTED, com.fooddelivery.order.service.state.OrderState::handleOrderCancelledByRestaurant);
        EVENT_HANDLERS.put(EventType.DRIVER_ASSIGNED, com.fooddelivery.order.service.state.OrderState::handleDriverAssigned);
        EVENT_HANDLERS.put(EventType.DISPATCH_FAILED, com.fooddelivery.order.service.state.OrderState::handleDispatchFailed);
        EVENT_HANDLERS.put(EventType.DELIVERY_FAILED, com.fooddelivery.order.service.state.OrderState::handleDeliveryFailed);
        EVENT_HANDLERS.put(EventType.ORDER_DELAY_APPROVAL_REQUESTED, com.fooddelivery.order.service.state.OrderState::handleDelayApprovalRequested);
        EVENT_HANDLERS.put(EventType.ORDER_DELAY_REJECTED, com.fooddelivery.order.service.state.OrderState::handleDelayRejected);
        EVENT_HANDLERS.put(EventType.ORDER_ACCEPTED, com.fooddelivery.order.service.state.OrderState::handleOrderAccepted);
        EVENT_HANDLERS.put(EventType.ORDER_READY, com.fooddelivery.order.service.state.OrderState::handleOrderReady);
    }
    private final org.springframework.web.client.RestTemplate restTemplate;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final com.fooddelivery.order.service.state.OrderActionService orderActionService;

    // We assume the system account ID for the platform is a fixed UUID for this prototype
    private static final UUID PLATFORM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final String paymentServiceBaseUrl = "http://payment-service";

    @Transactional
    public Order startOrderSaga(Order order) {
        Order savedOrder = orderRepository.save(order);
        
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .customerId(savedOrder.getCustomerId())
                .restaurantId(savedOrder.getRestaurantId())
                .totalAmount(savedOrder.getTotalAmount())
                .deliveryLat(savedOrder.getDeliveryLat())
                .deliveryLng(savedOrder.getDeliveryLng())
                .deliveryAddress(savedOrder.getDeliveryAddress())
                .build();
                
        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AppConstants.AGGREGATE_ORDER)
                    .aggregateId(savedOrder.getId().toString())
                    .eventType(EventType.ORDER_CREATED)
                    .payload(objectMapper.writeValueAsString(event))
                    .createdAt(LocalDateTime.now())
                    .build();
                    
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
                .eventType(event.getType())
                .payload(event.getPayload())
                .createdAt(LocalDateTime.now())
                .build();
                
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
            boolean isFailure = rootNode.has("failureReason");
            
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
            } catch (com.fooddelivery.order.service.state.IllegalStateTransitionException e) {
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
    public void handleOrderEvents(String payload, @org.springframework.messaging.handler.annotation.Header(value = "eventType", required = false) String headerEventType) {
        log.info("Received Order Event: {} with header type: {}", payload, headerEventType);
        
        int retries = 0;
        boolean success = false;
        while (!success && retries < AppConstants.MAX_OPTIMISTIC_LOCK_RETRIES) {
            try {
                Order orderToRefund = transactionTemplate.execute(status -> {
                    try {
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);
            String orderIdStr = rootNode.path("orderId").asText(null);
            String jsonEventType = rootNode.path("eventType").asText(null);
            String eventType = headerEventType != null ? headerEventType : jsonEventType;
            
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
                } else if (EventType.ORDER_STATUS_UPDATED.equals(eventType)) {
                    String updateStatus = rootNode.path("status").asText(null);
                    if (EventType.DELIVERY_FAILED.equals(updateStatus)) {
                        state.handleDeliveryFailed(context);
                    } else {
                        state.handleStatusUpdate(context);
                    }
                } else if (EventType.ORDER_DRIVER_REJECTED.equals(eventType)) {
                    log.info("Driver rejected/timed out ping for Order {}. Redispatching will be handled by DeliveryExecutiveApplication.", orderId);
                } else {
                    log.warn("Unmapped event type {} for Order {}. Ignoring.", eventType, orderId);
                }
            } catch (com.fooddelivery.order.service.state.IllegalStateTransitionException e) {
                log.error("ILLEGAL_STATE_TRANSITION: {}", e.getMessage());
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
            String eventType = approved ? EventType.ORDER_DELAY_APPROVED : EventType.ORDER_DELAY_REJECTED;
            com.fasterxml.jackson.databind.node.ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("eventType", eventType);
            payloadNode.put("orderId", order.getId().toString());
            payloadNode.put("restaurantId", order.getRestaurantId().toString());
            if (reason != null && !reason.isEmpty()) {
                payloadNode.put("reason", reason);
            }
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AppConstants.AGGREGATE_ORDER)
                    .aggregateId(order.getId().toString())
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(payloadNode))
                    .createdAt(LocalDateTime.now())
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.info("Saved outbox event {} for Order {}", eventType, order.getId());
        } catch (Exception e) {
            log.error("Failed to save delay approval event", e);
            throw new OrderProcessingException("Failed to process delay approval", e);
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
            if (PaymentIntentStatus.SUCCESS.equals(intent.getStatus()) || PaymentIntentStatus.CAPTURED.equalsIgnoreCase(intent.getStatus())) {
                try {
                    String refundUrl = paymentServiceBaseUrl + "/api/v1/payments/refund?gateway=" + intent.getGatewayName();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                    
                    Map<String, Object> request = new HashMap<>();
                    request.put("gatewayOrderId", intent.getGatewayOrderId());
                    request.put("amountInInr", order.getTotalAmount());
                    request.put("reason", "Order cancelled or rejected");
                    
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
                    ResponseEntity<String> response = restTemplate.postForEntity(refundUrl, entity, String.class);
                    
                    if (response.getStatusCode().is2xxSuccessful()) {
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
                                    
                                    UUID refundTransferId = UUID.nameUUIDFromBytes(("REFUND_" + order.getId()).getBytes());
                                    ledgerService.recordTransaction(refundTransferId, PLATFORM_ACCOUNT_ID, AppConstants.ACCOUNT_TYPE_PLATFORM, order.getCustomerId(), AppConstants.ACCOUNT_TYPE_CUSTOMER, order.getTotalAmount());
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
                        log.error("Failed to initiate refund via PaymentGatewayIntegration. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
                        transactionTemplate.executeWithoutResult(status -> {
                            intent.setStatus(PaymentIntentStatus.REFUND_FAILED);
                            paymentIntentRepository.save(intent);
                        });
                    }
                } catch (Exception e) {
                    log.error("Exception calling PaymentGatewayIntegration for refund on order {}. Marking as REFUND_FAILED.", order.getId(), e);
                    transactionTemplate.executeWithoutResult(status -> {
                        intent.setStatus(PaymentIntentStatus.REFUND_FAILED);
                        paymentIntentRepository.save(intent);
                    });
                }
            }
        });
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
                    .aggregateType(AppConstants.AGGREGATE_NOTIFICATION)
                    .aggregateId(customerId.toString())
                    .eventType(EventType.NOTIFICATION_REQUEST)
                    .payload(objectMapper.writeValueAsString(notificationEvent))
                    .createdAt(LocalDateTime.now())
                    .build();
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
               status == OrderStatus.DELIVERY_FAILED ||
               status == OrderStatus.PARTIALLY_REFUNDED ||
               status == OrderStatus.CANCELLED_AND_REFUNDED;
    }
}
