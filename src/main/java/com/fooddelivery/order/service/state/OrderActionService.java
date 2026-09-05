package com.fooddelivery.order.service.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.exception.OrderProcessingException;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import com.fooddelivery.common.constants.PaymentIntentStatus;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class OrderActionService {
    

    private final IOrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IPaymentIntentRepository paymentIntentRepository;
    private final ObjectMapper objectMapper;

    public void saveOrder(Order order) {
        orderRepository.save(order);
    }

    public void updatePaymentIntentStatus(UUID internalOrderId, PaymentIntentStatus status) {
        paymentIntentRepository.findByInternalOrderId(internalOrderId).ifPresent(intent -> {
            if (!status.equals(intent.getStatus())) {
                intent.setStatus(status);
                paymentIntentRepository.save(intent);
                log.info("PaymentIntent {} status updated to {}", intent.getId(), status);
            }
        });
    }



    public void emitOrderCancelledEvent(UUID orderId, String reason) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("eventType", EventType.ORDER_CANCELLED.name());
            payloadNode.put("orderId", orderId.toString());
            payloadNode.put("reason", reason);
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(EventType.ORDER_CANCELLED).payload(objectMapper.writeValueAsString(payloadNode)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for order: {}", EventType.ORDER_CANCELLED.name(), orderId);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_CANCELLED event", e);
            throw new OrderProcessingException("Failed to publish ORDER_CANCELLED event", e);
        }
    }

    public void emitOrderPartiallyRefundedEvent(UUID orderId, BigDecimal amount, String reason) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("eventType", "ORDER_PARTIALLY_REFUNDED");
            payloadNode.put("orderId", orderId.toString());
            payloadNode.put("amount", amount.toString());
            payloadNode.put("reason", reason);
            OutboxEventEntity outboxEvent =  // We'll assume the enum exists, or we use string representation in payload
            OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(EventType.valueOf("ORDER_PARTIALLY_REFUNDED")).payload(objectMapper.writeValueAsString(payloadNode)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: ORDER_PARTIALLY_REFUNDED for order: {} amount: {}", orderId, amount);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_PARTIALLY_REFUNDED event", e);
            throw new OrderProcessingException("Failed to publish ORDER_PARTIALLY_REFUNDED event", e);
        }
    }

    public void emitOrderCancelledByCustomerEvent(UUID orderId) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("eventType", EventType.ORDER_CANCELLED_BY_CUSTOMER.name());
            payloadNode.put("orderId", orderId.toString());
            payloadNode.put("reason", "Cancelled by customer");
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(EventType.ORDER_CANCELLED_BY_CUSTOMER).payload(objectMapper.writeValueAsString(payloadNode)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for order: {}", EventType.ORDER_CANCELLED_BY_CUSTOMER.name(), orderId);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_CANCELLED_BY_CUSTOMER event", e);
            throw new OrderProcessingException("Failed to publish ORDER_CANCELLED_BY_CUSTOMER event", e);
        }
    }

    public void emitOrderCancelledByRestaurantEvent(UUID orderId, String reason) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("eventType", EventType.ORDER_CANCELLED_BY_RESTAURANT.name());
            payloadNode.put("orderId", orderId.toString());
            payloadNode.put("reason", reason);
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(EventType.ORDER_CANCELLED_BY_RESTAURANT).payload(objectMapper.writeValueAsString(payloadNode)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for order: {}", EventType.ORDER_CANCELLED_BY_RESTAURANT.name(), orderId);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_CANCELLED_BY_RESTAURANT event", e);
            throw new OrderProcessingException("Failed to publish ORDER_CANCELLED_BY_RESTAURANT event", e);
        }
    }

    public void emitOrderDeliveryFailedEvent(UUID orderId, String reason) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("eventType", com.fooddelivery.common.constants.EventType.DELIVERY_FAILED.name());
            payloadNode.put("orderId", orderId.toString());
            payloadNode.put("reason", reason);
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(com.fooddelivery.common.constants.EventType.DELIVERY_FAILED).payload(objectMapper.writeValueAsString(payloadNode)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for order: {}", com.fooddelivery.common.constants.EventType.DELIVERY_FAILED.name(), orderId);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish DELIVERY_FAILED event", e);
            throw new OrderProcessingException("Failed to publish DELIVERY_FAILED event", e);
        }
    }

    public void emitOrderPaidEvent(Order order) {
        try {
            String itemsJsonStr = "[]";
            try {
                java.util.List<java.util.Map<String, Object>> itemList = order.getOrderItems().stream().map(i -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("name", i.getName());
                    map.put("quantity", i.getQuantity());
                    map.put("price", i.getPrice());
                    return map;
                }).collect(java.util.stream.Collectors.toList());
                itemsJsonStr = objectMapper.writeValueAsString(itemList);
            } catch (Exception ex) {
                log.error("Failed to serialize items", ex);
            }
            com.fooddelivery.common.event.OrderPaidEvent paidEvent = com.fooddelivery.common.event.OrderPaidEvent.builder()
                    .orderId(order.getId())
                    .restaurantId(order.getRestaurantId())
                    .customerName(order.getCustomerName())
                    .estimatedPrepTimeMinutes(order.getEstimatedPrepTimeMinutes())
                    .deliveryLat(order.getDeliveryLat())
                    .deliveryLng(order.getDeliveryLng())
                    .deliveryAddress(order.getDeliveryAddress())
                    .itemsJson(itemsJsonStr)
                    .pickupOtp(order.getPickupOtp())
                    .deliveryOtp(order.getOtp())
                    .totalAmount(order.getTotalAmount())
                    .itemTotal(order.getItemTotal())
                    .restaurantPlatformFee(order.getRestaurantPlatformFee())
                    .restaurantDeliveryContribution(order.getRestaurantDeliveryContribution())
                    .platformBonus(order.getPlatformBonus())
                    .restaurantPayout(order.getRestaurantPayout())
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(order.getId().toString()).eventType(EventType.ORDER_PAID).payload(objectMapper.writeValueAsString(paidEvent)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for order: {}", EventType.ORDER_PAID.name(), order.getId());
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_PAID event", e);
            throw new OrderProcessingException("Failed to publish ORDER_PAID event", e);
        }
    }

    public void emitOrderPlacedCodEvent(Order order) {
        try {
            String itemsJsonStr = "[]";
            try {
                java.util.List<java.util.Map<String, Object>> itemList = order.getOrderItems().stream().map(i -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("name", i.getName());
                    map.put("quantity", i.getQuantity());
                    map.put("price", i.getPrice());
                    return map;
                }).collect(java.util.stream.Collectors.toList());
                itemsJsonStr = objectMapper.writeValueAsString(itemList);
            } catch (Exception ex) {
                log.error("Failed to serialize items", ex);
            }
            com.fooddelivery.common.event.OrderPaidEvent paidEvent = com.fooddelivery.common.event.OrderPaidEvent.builder()
                    .orderId(order.getId())
                    .restaurantId(order.getRestaurantId())
                    .customerName(order.getCustomerName())
                    .estimatedPrepTimeMinutes(order.getEstimatedPrepTimeMinutes())
                    .deliveryLat(order.getDeliveryLat())
                    .deliveryLng(order.getDeliveryLng())
                    .deliveryAddress(order.getDeliveryAddress())
                    .itemsJson(itemsJsonStr)
                    .pickupOtp(order.getPickupOtp())
                    .deliveryOtp(order.getOtp())
                    .totalAmount(order.getTotalAmount())
                    .itemTotal(order.getItemTotal())
                    .restaurantPlatformFee(order.getRestaurantPlatformFee())
                    .restaurantDeliveryContribution(order.getRestaurantDeliveryContribution())
                    .platformBonus(order.getPlatformBonus())
                    .restaurantPayout(order.getRestaurantPayout())
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(order.getId().toString()).eventType(EventType.ORDER_PLACED_COD).payload(objectMapper.writeValueAsString(paidEvent)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for order: {}", EventType.ORDER_PLACED_COD.name(), order.getId());
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_PLACED_COD event", e);
            throw new OrderProcessingException("Failed to publish ORDER_PLACED_COD event", e);
        }
    }

    public void sendNotification(String orderId, UUID customerId, String templateCode) {
        try {
            com.fooddelivery.common.event.NotificationRequestEvent notificationEvent = com.fooddelivery.common.event.NotificationRequestEvent.builder().userId(customerId).channel(com.fooddelivery.common.enums.ChannelType.PUSH).eventName(templateCode).templateParams(java.util.List.of(orderId)).build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.NOTIFICATION).aggregateId(customerId.toString()).eventType(EventType.NOTIFICATION_REQUEST).payload(objectMapper.writeValueAsString(notificationEvent)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for customer: {}", EventType.NOTIFICATION_REQUEST.name(), customerId);
            outboxEventRepository.save(outboxEvent);
            log.info("Saved notification request to outbox for order {} to customer {}", orderId, customerId);
        } catch (Exception e) {
            log.error("Failed to save notification request to outbox", e);
            throw new RuntimeException("Failed to save notification", e);
        }
    }

    public void emitOrderStatusSyncEvent(UUID orderId, OrderStatus currentStatus) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("orderId", orderId.toString());
            payload.put("status", currentStatus.name());
            payload.put("eventType", EventType.ORDER_STATUS_SYNC.name());
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(EventType.ORDER_STATUS_SYNC).payload(objectMapper.writeValueAsString(payload)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            outboxEventRepository.save(outboxEvent);
            log.info("Saved ORDER_STATUS_SYNC event to outbox for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to save ORDER_STATUS_SYNC outbox event for order: {}", orderId, e);
            throw new OrderProcessingException("Failed to emit ORDER_STATUS_SYNC", e);
        }
    }



    
}
