package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.event.OrderCreatedEvent;
import com.fooddelivery.common.constants.KafkaConstants;
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
                .build();
                
        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType("Order")
                    .aggregateId(savedOrder.getId().toString())
                    .eventType("ORDER_CREATED")
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

    // Listens to Kafka 'payment-events' topic for PaymentSucceededEvent published by external Payment Service
    @Transactional
    @KafkaListener(topics = KafkaConstants.TOPIC_PAYMENT_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY)
    public void handlePaymentSuccess(String payload) {
        log.info("Received PaymentSucceededEvent: {}", payload);
        
        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);
            
            // Expected payload: {"orderId":"...","gatewayOrderId":"...","amount":300.00,"gatewayName":"VYAPAR"}
            if (!rootNode.has("orderId") || !rootNode.has("gatewayOrderId")) {
                log.info("Ignoring unrecognized event payload: {}", payload);
                return;
            }
            
            String gatewayOrderId = rootNode.get("gatewayOrderId").asText();
            String internalOrderId = rootNode.get("orderId").asText();
            
            log.info("Payment succeeded for gateway order {}. Finding internal order {}.", gatewayOrderId, internalOrderId);
            
            com.fooddelivery.order.entity.PaymentIntent intent = paymentIntentRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
            if (intent == null) {
                log.warn("PaymentIntent for gateway order {} not found", gatewayOrderId);
                return;
            }
            
            UUID orderUUID = intent.getInternalOrderId();
            Order order = orderRepository.findById(orderUUID).orElse(null);
            if (order == null) {
                log.warn("Order {} not found, skipping status update", orderUUID);
                return;
            }
            if (order.getStatus() == OrderStatus.PAID) {
                log.info("Order {} is already PAID. Ignoring duplicate event.", orderUUID);
                return;
            }
            
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
            
            com.fooddelivery.common.event.OrderPaidEvent paidEvent = com.fooddelivery.common.event.OrderPaidEvent.builder()
                    .orderId(order.getId())
                    .restaurantId(order.getRestaurantId())
                    .build();
            
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType("Order")
                    .aggregateId(order.getId().toString())
                    .eventType("ORDER_PAID")
                    .payload(objectMapper.writeValueAsString(paidEvent))
                    .createdAt(LocalDateTime.now())
                    .status("UNPROCESSED")
                    .build();
            outboxEventRepository.save(outboxEvent);
            
            log.info("Order {} status updated to PAID and outbox event saved", orderUUID);
            
            // Update PaymentIntent in the same transaction
            if (!"SUCCESS".equals(intent.getStatus())) {
                intent.setStatus("SUCCESS");
                paymentIntentRepository.save(intent);
                log.info("PaymentIntent {} status updated to SUCCESS", intent.getId());
            }
            
        } catch (Exception e) {
            log.error("Error processing payment event payload", e);
            throw new RuntimeException("Failed to process payment event", e);
        }
    }

    // Listens to Kafka 'order-events' topic for ORDER_ACCEPTED
    @Transactional
    @KafkaListener(topics = "order-events", groupId = KafkaConstants.GROUP_FOOD_DELIVERY)
    public void handleOrderEvents(String payload, @org.springframework.messaging.handler.annotation.Header(value = "eventType", required = false) String headerEventType) {
        log.info("Received Order Event: {} with header type: {}", payload, headerEventType);
        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);
            String orderIdStr = rootNode.path("orderId").asText(null);
            String jsonEventType = rootNode.path("eventType").asText(null);
            String eventType = headerEventType != null ? headerEventType : jsonEventType;
            
            if (orderIdStr == null || eventType == null) {
                log.warn("Missing orderId or eventType. Ignored.");
                return;
            }
            
            UUID orderId = UUID.fromString(orderIdStr);
            
            if ("ORDER_DELIVERED".equals(eventType)) {
                log.info("Order {} delivered. Processing ledger accounting.", orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && order.getStatus() != OrderStatus.DELIVERED) {
                    order.setStatus(OrderStatus.DELIVERED);
                    orderRepository.save(order);
                    
                    // Calculate splits: 80% to restaurant, 20% to platform.
                    // For simplicity, driver gets flat 50.0.
                    java.math.BigDecimal total = order.getTotalAmount();
                    java.math.BigDecimal restPayout = total.multiply(new java.math.BigDecimal("0.80"));
                    java.math.BigDecimal driverPayout = new java.math.BigDecimal("50.00");
                    
                    UUID restTransferId = UUID.nameUUIDFromBytes(("REST_PAYOUT_" + orderId).getBytes());
                    ledgerService.recordTransaction(restTransferId, PLATFORM_ACCOUNT_ID, "PLATFORM", order.getRestaurantId(), "RESTAURANT", restPayout);
                    
                    if (order.getDeliveryExecutiveId() != null) {
                        UUID driverTransferId = UUID.nameUUIDFromBytes(("DRIVER_PAYOUT_" + orderId).getBytes());
                        ledgerService.recordTransaction(driverTransferId, PLATFORM_ACCOUNT_ID, "PLATFORM", order.getDeliveryExecutiveId(), "DRIVER", driverPayout);
                    }
                    
                    // Send notification to customer
                    sendNotification(orderId.toString(), order.getCustomerId(), "ORDER_DELIVERED");
                }
                return;
            }
            
            if ("ORDER_CANCELLED_BY_RESTAURANT".equals(eventType) || "ORDER_REJECTED".equals(eventType)) {
                log.info("Order {} cancelled/rejected by restaurant. Processing refund.", orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null) {
                    order.setStatus(OrderStatus.DELIVERY_FAILED); // or a CANCELLED status if it existed
                    orderRepository.save(order);
                    processRefund(order);
                }
                return;
            }
            
            if ("ORDER_STATUS_UPDATED".equals(eventType)) {
                String updateStatus = rootNode.path("status").asText(null);
                if ("DELIVERY_FAILED".equals(updateStatus)) {
                    log.info("Order {} delivery failed. Processing refund.", orderId);
                    Order order = orderRepository.findById(orderId).orElse(null);
                    if (order != null) {
                        processRefund(order);
                    }
                } else if (updateStatus != null) {
                    log.info("Order {} status updated to {}.", orderId, updateStatus);
                    Order order = orderRepository.findById(orderId).orElse(null);
                    if (order != null) {
                        try {
                            OrderStatus newStatus = OrderStatus.valueOf(updateStatus);
                            order.setStatus(newStatus);
                            orderRepository.save(order);
                        } catch (IllegalArgumentException e) {
                            log.warn("Unknown OrderStatus: {}", updateStatus);
                        }
                    }
                }
                return;
            }
            
            if ("ORDER_DRIVER_REJECTED".equals(eventType)) {
                log.info("Driver rejected/timed out ping for Order {}. Redispatching will be handled by DeliveryExecutiveApplication.", orderId);
                // DeliveryExecutiveApplication is listening to this event and will handle redispatch
                return;
            }
            
            if ("DRIVER_ASSIGNED".equals(eventType)) {
                String driverIdStr = rootNode.path("driverId").asText(null);
                log.info("Driver {} assigned to Order {}. Updating status to DISPATCHED.", driverIdStr, orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null) {
                    UUID driverUUID;
                    try {
                        driverUUID = UUID.fromString(driverIdStr);
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid UUID format for driverId: {}. Skipping event.", driverIdStr);
                        return;
                    }
                    order.setDeliveryExecutiveId(driverUUID);
                    order.setStatus(OrderStatus.DISPATCHED);
                    orderRepository.save(order);
                    
                    // Send notification to the CUSTOMER that the driver is on the way
                    sendNotification(orderId.toString(), order.getCustomerId(), "DRIVER_ON_THE_WAY");
                }
                return;
            }
            
            if ("DISPATCH_FAILED".equals(eventType)) {
                log.warn("Failed to assign driver for Order {}. Processing refund.", orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null) {
                    order.setStatus(OrderStatus.DELIVERY_FAILED);
                    orderRepository.save(order);
                    processRefund(order);
                }
                return;
            }
            

            if ("ORDER_ACCEPTED".equals(eventType)) {
                log.info("Order {} accepted by restaurant.", orderId);
                
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null && order.getStatus() == OrderStatus.PAID) {
                    order.setStatus(OrderStatus.ACCEPTED);
                    orderRepository.save(order);
                    log.info("Order {} status updated to ACCEPTED.", orderId);
                    
                    // Dispatch logic will now be handled by DeliveryExecutiveApplication listening to this same event
                } else {
                    log.info("Order {} is not in PAID state or not found. Cannot accept.", orderId);
                }
                return;
            }
            
            if ("ORDER_READY".equals(eventType)) {
                log.info("Order {} is ready for pickup.", orderId);
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null) {
                    order.setStatus(OrderStatus.READY_FOR_PICKUP);
                    orderRepository.save(order);
                }
                return;
            }
        } catch (Exception e) {
            log.error("Error processing order event", e);
            throw new RuntimeException("Failed to process order event", e);
        }
    }

    
    
    @Transactional
    protected void processRefund(Order order) {
        log.info("Processing refund for Order {}", order.getId());
        paymentIntentRepository.findByInternalOrderId(order.getId()).ifPresent(intent -> {
            if ("SUCCESS".equals(intent.getStatus()) || "CAPTURED".equalsIgnoreCase(intent.getStatus())) {
                try {
                    String refundUrl = paymentServiceBaseUrl + "/api/v1/payments/refund?gateway=VYAPAR";
                    // use injected restTemplate
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                    
                    Map<String, Object> request = new HashMap<>();
                    request.put("gatewayOrderId", intent.getGatewayOrderId());
                    request.put("amountInInr", order.getTotalAmount());
                    request.put("reason", "Order cancelled or rejected");
                    
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
                    ResponseEntity<String> response = restTemplate.postForEntity(refundUrl, entity, String.class);
                    
                    if (response.getStatusCode().is2xxSuccessful()) {
                        intent.setStatus("REFUNDED");
                        paymentIntentRepository.save(intent);
                        log.info("PaymentIntent {} status updated to REFUNDED. Funds returned to Customer via Gateway.", intent.getId());
                        
                        // Ledger reverse transaction
                        UUID refundTransferId = UUID.nameUUIDFromBytes(("REFUND_" + order.getId()).getBytes());
                        ledgerService.recordTransaction(refundTransferId, PLATFORM_ACCOUNT_ID, "PLATFORM", order.getCustomerId(), "CUSTOMER", order.getTotalAmount());
                    } else {
                        log.error("Failed to initiate refund via PaymentGatewayIntegration. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
                    }
                } catch (Exception e) {
                    log.error("Exception calling PaymentGatewayIntegration for refund on order {}", order.getId(), e);
                    throw new RuntimeException("Failed to initiate refund", e);
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
            
            kafkaTemplate.send(KafkaConstants.TOPIC_NOTIFICATIONS_DISPATCH, customerId.toString(), objectMapper.writeValueAsString(notificationEvent));
            log.info("Sent notification request for order {} to customer {}", orderId, customerId);
        } catch (Exception e) {
            log.error("Failed to send notification request", e);
            throw new RuntimeException("Failed to send notification", e);
        }
    }
}
