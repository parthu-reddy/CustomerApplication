package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.event.OrderCreatedEvent;
import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.exception.OrderProcessingException;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OutboxEventEntity;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IOutboxEventRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {
    
    private final IOrderRepository orderRepository;
    private final IOutboxEventRepository outboxEventRepository;
    private final IPaymentIntentRepository paymentIntentRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DoubleEntryLedgerService ledgerService;
    private final org.springframework.web.client.RestTemplate restTemplate;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    // We assume the system account ID for the platform is a fixed UUID for this prototype
    private static final UUID PLATFORM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Value("${payment-service.base-url}")
    private String paymentServiceBaseUrl;

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

            if (isFailure) {
                if (order.getStatus() != OrderStatus.CREATED) {
                    log.error("ILLEGAL_STATE_TRANSITION: Ignoring payment failure for order {} as it is not in CREATED state (Current status: {})", orderUUID, order.getStatus());
                    return null;
                }
                log.info("Payment failed for Order {}. Cancelling order.", orderUUID);
                if (!isTerminalState(order.getStatus())) {
                    order.setStatus(OrderStatus.CANCELLED);
                    orderRepository.save(order);
                }
                if (!PaymentIntentStatus.FAILED.equals(intent.getStatus())) {
                    intent.setStatus(PaymentIntentStatus.FAILED);
                    paymentIntentRepository.save(intent);
                }
                return null;
            }

            // Success scenario
            if (order.getStatus() != OrderStatus.CREATED) {
                log.error("ILLEGAL_STATE_TRANSITION: Order {} is not in CREATED state (Current status: {}). Ignoring payment success event.", orderUUID, order.getStatus());
                if (!PaymentIntentStatus.SUCCESS.equals(intent.getStatus())) {
                    intent.setStatus(PaymentIntentStatus.SUCCESS);
                    paymentIntentRepository.save(intent);
                    log.info("PaymentIntent {} status updated to SUCCESS", intent.getId());
                    
                    // Record late payment from customer to platform
                    UUID paymentTransferId = UUID.nameUUIDFromBytes(("PAYMENT_" + orderUUID).getBytes());
                    ledgerService.recordTransaction(paymentTransferId, order.getCustomerId(), AppConstants.ACCOUNT_TYPE_CUSTOMER, PLATFORM_ACCOUNT_ID, AppConstants.ACCOUNT_TYPE_PLATFORM, order.getTotalAmount());
                    
                    if (isTerminalState(order.getStatus()) && order.getStatus() != OrderStatus.DELIVERED) {
                        log.info("Order {} is in terminal state {}. Processing immediate refund for late payment success.", orderUUID, order.getStatus());
                        return order;
                    }
                }
                return null;
            }
            
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
            
            // Record initial payment from customer to platform
            UUID paymentTransferId = UUID.nameUUIDFromBytes(("PAYMENT_" + orderUUID).getBytes());
            ledgerService.recordTransaction(paymentTransferId, order.getCustomerId(), AppConstants.ACCOUNT_TYPE_CUSTOMER, PLATFORM_ACCOUNT_ID, AppConstants.ACCOUNT_TYPE_PLATFORM, order.getTotalAmount());
            
            com.fooddelivery.common.event.OrderPaidEvent paidEvent = com.fooddelivery.common.event.OrderPaidEvent.builder()
                    .orderId(order.getId())
                    .restaurantId(order.getRestaurantId())
                    .estimatedPrepTimeMinutes(order.getEstimatedPrepTimeMinutes())
                    .deliveryLat(order.getDeliveryLat())
                    .deliveryLng(order.getDeliveryLng())
                    .deliveryAddress(order.getDeliveryAddress())
                    .build();
            
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AppConstants.AGGREGATE_ORDER)
                    .aggregateId(order.getId().toString())
                    .eventType(EventType.ORDER_PAID)
                    .payload(objectMapper.writeValueAsString(paidEvent))
                    .createdAt(LocalDateTime.now())
                    .status(AppConstants.OUTBOX_STATUS_UNPROCESSED)
                    .build();
            outboxEventRepository.save(outboxEvent);
            
            // Send notification to customer
            sendNotification(orderUUID.toString(), order.getCustomerId(), EventType.ORDER_PAID);
            
            log.info("Order {} status updated to PAID and outbox event saved", orderUUID);
            
            // Update PaymentIntent in the same transaction
            if (!PaymentIntentStatus.SUCCESS.equals(intent.getStatus())) {
                intent.setStatus(PaymentIntentStatus.SUCCESS);
                paymentIntentRepository.save(intent);
                log.info("PaymentIntent {} status updated to SUCCESS", intent.getId());
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
            
            if (EventType.ORDER_DELIVERED.equals(eventType)) {
                log.info("Order {} delivered. Processing ledger accounting.", orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && (order.getStatus() == OrderStatus.DISPATCHED || order.getStatus() == OrderStatus.READY_FOR_PICKUP || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY)) {
                    order.setStatus(OrderStatus.DELIVERED);
                    orderRepository.save(order);
                    
                    // Calculate splits: 80% to restaurant, 20% to platform.
                    // For simplicity, driver gets flat 50.0.
                    java.math.BigDecimal total = order.getTotalAmount();
                    java.math.BigDecimal restPayout = total.multiply(new java.math.BigDecimal("0.80"));
                    java.math.BigDecimal driverPayout = new java.math.BigDecimal("50.00");
                    
                    UUID restTransferId = UUID.nameUUIDFromBytes(("REST_PAYOUT_" + orderId).getBytes());
                    ledgerService.recordTransaction(restTransferId, PLATFORM_ACCOUNT_ID, AppConstants.ACCOUNT_TYPE_PLATFORM, order.getRestaurantId(), AppConstants.ACCOUNT_TYPE_RESTAURANT, restPayout);
                    
                    if (order.getDeliveryExecutiveId() != null) {
                        UUID driverTransferId = UUID.nameUUIDFromBytes(("DRIVER_PAYOUT_" + orderId).getBytes());
                        ledgerService.recordTransaction(driverTransferId, PLATFORM_ACCOUNT_ID, AppConstants.ACCOUNT_TYPE_PLATFORM, order.getDeliveryExecutiveId(), AppConstants.ACCOUNT_TYPE_DRIVER, driverPayout);
                    }
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), EventType.ORDER_DELIVERED);
                } else if (order != null) {
                    if (OrderStatus.DELIVERED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_DELIVERED for Order {}. Current status {} is further along.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_DELIVERED for Order {}. Current status is {}. Allowed previous states are DISPATCHED, READY_FOR_PICKUP, OUT_FOR_DELIVERY.", orderId, order.getStatus());
                    }
                }
                return null;
            }
            
            if (EventType.ORDER_CANCELLED_BY_RESTAURANT.equals(eventType) || EventType.ORDER_REJECTED.equals(eventType)) {
                log.info("Order {} cancelled/rejected by restaurant. Processing refund.", orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.AWAITING_DELAY_APPROVAL || order.getStatus() == OrderStatus.ACCEPTED || order.getStatus() == OrderStatus.DISPATCHED || order.getStatus() == OrderStatus.READY_FOR_PICKUP)) {
                    order.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
                    order.setCancellationReason(rootNode.path("reason").asText("Restaurant could not fulfill the order"));
                    orderRepository.save(order);
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), EventType.ORDER_CANCELLED_BY_RESTAURANT);
                    return order; // Need refund
                } else if (order != null) {
                    if (OrderStatus.CANCELLED_BY_RESTAURANT.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process {} for Order {}. Current status is {}.", eventType, orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process {} for Order {}. Current status is {}. Allowed previous states are PAID, AWAITING_DELAY_APPROVAL, ACCEPTED, DISPATCHED, READY_FOR_PICKUP.", eventType, orderId, order.getStatus());
                    }
                }
                return null;
            }
            
            if (EventType.ORDER_STATUS_UPDATED.equals(eventType)) {
                String updateStatus = rootNode.path("status").asText(null);
                if (EventType.DELIVERY_FAILED.equals(updateStatus)) {
                    log.info("Order {} delivery failed. Processing refund.", orderId);
                    Order order = orderRepository.findById(orderId).orElse(null);
                    if (order != null && (order.getStatus() == OrderStatus.DISPATCHED || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY || order.getStatus() == OrderStatus.READY_FOR_PICKUP)) {
                        order.setStatus(OrderStatus.DELIVERY_FAILED);
                        orderRepository.save(order);
                        
                        // Send notification to customer
                        sendNotification(orderId.toString(), order.getCustomerId(), EventType.DELIVERY_FAILED);
                        return order; // Need refund
                    } else if (order != null) {
                        if (OrderStatus.DELIVERY_FAILED.ordinal() < order.getStatus().ordinal()) {
                            log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process DELIVERY_FAILED status update for Order {}. Current status is {}.", orderId, order.getStatus());
                        } else {
                            log.error("ILLEGAL_STATE_TRANSITION: Cannot process DELIVERY_FAILED status update for Order {}. Current status is {}. Allowed previous states are DISPATCHED, OUT_FOR_DELIVERY, READY_FOR_PICKUP.", orderId, order.getStatus());
                        }
                    }
                } else if (updateStatus != null) {
                    log.info("Order {} status updated to {}.", orderId, updateStatus);
                    Order order = orderRepository.findById(orderId).orElse(null);
                    if (order != null && !isTerminalState(order.getStatus())) {
                        try {
                            OrderStatus newStatus = OrderStatus.valueOf(updateStatus);
                            boolean validTransition = false;
                            
                            if (newStatus == OrderStatus.READY_FOR_PICKUP && (order.getStatus() == OrderStatus.ACCEPTED || order.getStatus() == OrderStatus.DISPATCHED)) {
                                validTransition = true;
                            } else if (newStatus == OrderStatus.OUT_FOR_DELIVERY && (order.getStatus() == OrderStatus.DISPATCHED || order.getStatus() == OrderStatus.READY_FOR_PICKUP)) {
                                validTransition = true;
                            }
                            
                            if (validTransition) {
                                order.setStatus(newStatus);
                                orderRepository.save(order);
                                sendNotification(orderId.toString(), order.getCustomerId(), "ORDER_STATUS_" + updateStatus);
                            } else {
                                if (newStatus.ordinal() < order.getStatus().ordinal()) {
                                    log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process status update to {} for Order {}. Current status is {}.", updateStatus, orderId, order.getStatus());
                                } else {
                                    log.error("ILLEGAL_STATE_TRANSITION: Cannot process status update to {} for Order {}. Current status is {}.", updateStatus, orderId, order.getStatus());
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            log.warn("Unknown OrderStatus: {}", updateStatus);
                        }
                    } else if (order != null) {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process status update to {} for Order {}. Current status {} is already terminal.", updateStatus, orderId, order.getStatus());
                    }
                }
                return null;
            }
            
            if (EventType.ORDER_DRIVER_REJECTED.equals(eventType)) {
                log.info("Driver rejected/timed out ping for Order {}. Redispatching will be handled by DeliveryExecutiveApplication.", orderId);
                // DeliveryExecutiveApplication is listening to this event and will handle redispatch
                return null;
            }
            
            if (EventType.DRIVER_ASSIGNED.equals(eventType)) {
                String driverIdStr = rootNode.path("driverId").asText(null);
                log.info("Driver {} assigned to Order {}. Updating status.", driverIdStr, orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && (order.getStatus() == OrderStatus.ACCEPTED || order.getStatus() == OrderStatus.READY_FOR_PICKUP || order.getStatus() == OrderStatus.DISPATCHED)) {
                    UUID driverUUID;
                    try {
                        driverUUID = UUID.fromString(driverIdStr);
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid UUID format for driverId: {}. Skipping event.", driverIdStr);
                        return null;
                    }
                    order.setDeliveryExecutiveId(driverUUID);
                    if (order.getStatus() == OrderStatus.READY_FOR_PICKUP) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Order {} is already READY_FOR_PICKUP. DRIVER_ASSIGNED event will not revert status to DISPATCHED.", orderId);
                    } else if (order.getStatus() == OrderStatus.DISPATCHED) {
                        log.info("Order {} is already DISPATCHED. Updating driverId only.", orderId);
                    } else {
                        order.setStatus(OrderStatus.DISPATCHED);
                    }
                    orderRepository.save(order);
                    
                    // Send notification to the CUSTOMER that the driver is on the way
                    sendNotification(orderId.toString(), order.getCustomerId(), EventType.NOTIFY_DRIVER_ON_THE_WAY);
                } else if (order != null) {
                    log.error("ILLEGAL_STATE_TRANSITION: Cannot process DRIVER_ASSIGNED for Order {}. Current status is {}. Allowed previous states are ACCEPTED, READY_FOR_PICKUP, DISPATCHED.", orderId, order.getStatus());
                }
                return null;
            }
            
            if (EventType.DISPATCH_FAILED.equals(eventType)) {
                log.warn("Failed to assign driver for Order {}. Processing refund.", orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && (order.getStatus() == OrderStatus.ACCEPTED || order.getStatus() == OrderStatus.READY_FOR_PICKUP || order.getStatus() == OrderStatus.DISPATCHED)) {
                    order.setStatus(OrderStatus.DELIVERY_FAILED);
                    orderRepository.save(order);
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), EventType.DISPATCH_FAILED);
                    return order; // Need refund
                } else if (order != null) {
                    if (OrderStatus.DELIVERY_FAILED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process DISPATCH_FAILED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process DISPATCH_FAILED for Order {}. Current status is {}. Allowed previous states are ACCEPTED, READY_FOR_PICKUP, DISPATCHED.", orderId, order.getStatus());
                    }
                }
                return null;
            }
            
            if (EventType.ORDER_DELAY_APPROVAL_REQUESTED.equals(eventType)) {
                log.info("Restaurant requested delay approval for Order {}", orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && order.getStatus() == OrderStatus.PAID) {
                    order.setStatus(OrderStatus.AWAITING_DELAY_APPROVAL);
                    orderRepository.save(order);
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), EventType.NOTIFY_DELAY_APPROVAL_REQUESTED);
                } else if (order != null) {
                    if (OrderStatus.AWAITING_DELAY_APPROVAL.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_DELAY_APPROVAL_REQUESTED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_DELAY_APPROVAL_REQUESTED for Order {}. Current status is {}. Expected PAID.", orderId, order.getStatus());
                    }
                }
                return null;
            }
            
            if (EventType.ORDER_DELAY_REJECTED.equals(eventType)) {
                log.info("Customer rejected delay for Order {}. Processing refund.", orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && order.getStatus() == OrderStatus.AWAITING_DELAY_APPROVAL) {
                    order.setStatus(OrderStatus.CANCELLED);
                    orderRepository.save(order);
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), EventType.ORDER_DELAY_REJECTED);
                    return order; // Need refund
                } else if (order != null) {
                    if (OrderStatus.CANCELLED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_DELAY_REJECTED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_DELAY_REJECTED for Order {}. Current status is {}. Expected AWAITING_DELAY_APPROVAL.", orderId, order.getStatus());
                    }
                }
                return null;
            }
            

            if (EventType.ORDER_ACCEPTED.equals(eventType)) {
                log.info("Order {} accepted by restaurant.", orderId);
                
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.AWAITING_DELAY_APPROVAL)) {
                    order.setStatus(OrderStatus.ACCEPTED);
                    
                    com.fasterxml.jackson.databind.JsonNode prepNode = rootNode.path("estimatedPrepTimeMinutes");
                    if (!prepNode.isMissingNode() && !prepNode.isNull()) {
                        order.setEstimatedPrepTimeMinutes(prepNode.asInt());
                    }
                    
                    orderRepository.save(order);
                    log.info("Order {} status updated to ACCEPTED.", orderId);
                    
                    // Dispatch logic will now be handled by DeliveryExecutiveApplication listening to this same event
                } else if (order != null) {
                    if (OrderStatus.ACCEPTED.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_ACCEPTED for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_ACCEPTED for Order {}. Current status is {}. Expected PAID or AWAITING_DELAY_APPROVAL.", orderId, order.getStatus());
                    }
                }
                return null;
            }
            
            if (EventType.ORDER_READY.equals(eventType)) {
                log.info("Order {} is ready for pickup.", orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && (order.getStatus() == OrderStatus.ACCEPTED || order.getStatus() == OrderStatus.DISPATCHED)) {
                    order.setStatus(OrderStatus.READY_FOR_PICKUP);
                    orderRepository.save(order);
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), EventType.NOTIFY_ORDER_READY_FOR_PICKUP);
                } else if (order != null) {
                    if (OrderStatus.READY_FOR_PICKUP.ordinal() < order.getStatus().ordinal()) {
                        log.error("BACKWARD_STATE_TRANSITION_ATTEMPT: Cannot process ORDER_READY for Order {}. Current status is {}.", orderId, order.getStatus());
                    } else {
                        log.error("ILLEGAL_STATE_TRANSITION: Cannot process ORDER_READY for Order {}. Current status is {}. Expected ACCEPTED or DISPATCHED.", orderId, order.getStatus());
                    }
                }
                return null;
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
    public void publishDelayApprovalEvent(Order order, boolean approved) {
        try {
            String eventType = approved ? EventType.ORDER_DELAY_APPROVED : EventType.ORDER_DELAY_REJECTED;
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(AppConstants.AGGREGATE_ORDER)
                    .aggregateId(order.getId().toString())
                    .eventType(eventType)
                    .payload(String.format("{\"eventType\":\"%s\", \"orderId\":\"%s\", \"restaurantId\":\"%s\"}",
                            eventType, order.getId(), order.getRestaurantId()))
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
            
            boolean updated = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                Order dbOrder = orderRepository.findById(order.getId()).orElse(null);
                if (dbOrder != null && dbOrder.getStatus() == OrderStatus.AWAITING_DELAY_APPROVAL) {
                    dbOrder.setStatus(OrderStatus.CANCELLED);
                    dbOrder.setCancellationReason("Auto-cancelled: Customer did not respond to delay approval in 10 minutes");
                    orderRepository.save(dbOrder);
                    return true;
                }
                return false;
            }));
            
            if (updated) {
                // Refund the customer (Outside transaction!)
                processRefund(order);
                
                // Publish rejection event to restaurant to let them know it was auto-cancelled
                publishDelayApprovalEvent(order, false);
                
                // Send notification to customer
                sendNotification(order.getId().toString(), order.getCustomerId(), EventType.NOTIFY_ORDER_CANCELLED_DELAY_TIMEOUT);
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
                                    intent.setStatus(PaymentIntentStatus.REFUNDED);
                                    paymentIntentRepository.save(intent);
                                    
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
