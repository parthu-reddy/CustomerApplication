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
            com.fooddelivery.common.event.OrderCancelledEvent event = com.fooddelivery.common.event.OrderCancelledEvent.builder()
                    .orderId(orderId.toString())
                    .reason(reason)
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(EventType.ORDER_CANCELLED).payload(objectMapper.writeValueAsString(event)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for order: {}", EventType.ORDER_CANCELLED.name(), orderId);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_CANCELLED event", e);
            throw new OrderProcessingException("Failed to publish ORDER_CANCELLED event", e);
        }
    }

    public void emitOrderPartiallyRefundedEvent(UUID orderId, BigDecimal amount, String reason) {
        try {
            com.fooddelivery.common.event.OrderPartiallyRefundedEvent event = com.fooddelivery.common.event.OrderPartiallyRefundedEvent.builder()
                    .orderId(orderId.toString())
                    .amount(amount)
                    .reason(reason)
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(EventType.valueOf("ORDER_PARTIALLY_REFUNDED")).payload(objectMapper.writeValueAsString(event)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: ORDER_PARTIALLY_REFUNDED for order: {} amount: {}", orderId, amount);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_PARTIALLY_REFUNDED event", e);
            throw new OrderProcessingException("Failed to publish ORDER_PARTIALLY_REFUNDED event", e);
        }
    }

    public void emitOrderCancelledByCustomerEvent(UUID orderId) {
        try {
            com.fooddelivery.common.event.OrderCancelledByCustomerEvent event = com.fooddelivery.common.event.OrderCancelledByCustomerEvent.builder()
                    .orderId(orderId.toString())
                    .reason("Cancelled by customer")
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(EventType.ORDER_CANCELLED_BY_CUSTOMER).payload(objectMapper.writeValueAsString(event)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for order: {}", EventType.ORDER_CANCELLED_BY_CUSTOMER.name(), orderId);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_CANCELLED_BY_CUSTOMER event", e);
            throw new OrderProcessingException("Failed to publish ORDER_CANCELLED_BY_CUSTOMER event", e);
        }
    }

    public void emitOrderCancelledByRestaurantEvent(UUID orderId, String reason) {
        try {
            com.fooddelivery.common.event.OrderCancelledByRestaurantEvent event = com.fooddelivery.common.event.OrderCancelledByRestaurantEvent.builder()
                    .orderId(orderId.toString())
                    .reason(reason)
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(EventType.ORDER_CANCELLED_BY_RESTAURANT).payload(objectMapper.writeValueAsString(event)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for order: {}", EventType.ORDER_CANCELLED_BY_RESTAURANT.name(), orderId);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_CANCELLED_BY_RESTAURANT event", e);
            throw new OrderProcessingException("Failed to publish ORDER_CANCELLED_BY_RESTAURANT event", e);
        }
    }

    public void emitOrderDeliveryFailedEvent(UUID orderId, String reason) {
        try {
            com.fooddelivery.common.event.DeliveryFailedEvent event = com.fooddelivery.common.event.DeliveryFailedEvent.builder()
                    .orderId(orderId.toString())
                    .reason(reason)
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(com.fooddelivery.common.constants.EventType.DELIVERY_FAILED).payload(objectMapper.writeValueAsString(event)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for order: {}", com.fooddelivery.common.constants.EventType.DELIVERY_FAILED.name(), orderId);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish DELIVERY_FAILED event", e);
            throw new OrderProcessingException("Failed to publish DELIVERY_FAILED event", e);
        }
    }

    public void emitOrderPaidEvent(Order order) {
        emitOrderPlacedEvent(order, EventType.ORDER_PAID);
    }

    public void emitOrderPlacedCodEvent(Order order) {
        emitOrderPlacedEvent(order, EventType.ORDER_PLACED_COD);
    }

    /**
     * The one builder for both. They were byte-identical apart from the event type, which is how
     * {@code customerId} came to be missing from an event the restaurant service reads it from.
     */
    private void emitOrderPlacedEvent(Order order, EventType eventType) {
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
                    .customerId(order.getCustomerId())
                    .customerName(order.getCustomerName())
                    .paymentMethod(order.getPaymentMethod())
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
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(order.getId().toString()).eventType(eventType).payload(objectMapper.writeValueAsString(paidEvent)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            log.info("Triggering event: {} for order: {}", eventType.name(), order.getId());
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish {} event", eventType.name(), e);
            throw new OrderProcessingException("Failed to publish " + eventType.name() + " event", e);
        }
    }

    public void sendNotification(String orderId, UUID customerId, com.fooddelivery.common.constants.NotificationTemplate templateCode) {
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
            com.fooddelivery.common.event.OrderStatusSyncEvent event = com.fooddelivery.common.event.OrderStatusSyncEvent.builder()
                    .orderId(orderId.toString())
                    .status(currentStatus.name())
                    .build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(orderId.toString()).eventType(EventType.ORDER_STATUS_SYNC).payload(objectMapper.writeValueAsString(event)).createdAt(LocalDateTime.now()).status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED).build();
            outboxEventRepository.save(outboxEvent);
            log.info("Saved ORDER_STATUS_SYNC event to outbox for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to save ORDER_STATUS_SYNC outbox event for order: {}", orderId, e);
            throw new OrderProcessingException("Failed to emit ORDER_STATUS_SYNC", e);
        }
    }

    /**
     * Completes a delivery. The single definition of what that means.
     *
     * <p>It lived in two states that disagreed. {@code HandedOverState} booked the order economics,
     * collected the COD cash and moved the intent to COLLECTED; {@code ReadyForPickupState} — which
     * is reached whenever ORDER_DELIVERED is processed while the handover event has been lost,
     * retried into the DLT, or simply arrived out of order — booked the economics only. The
     * restaurant and the rider were credited against cash the book said was never received, and a
     * later refund on that order routed to NONE because the intent still said PENDING_COLLECTION.
     */
    public void completeDelivery(com.fooddelivery.order.service.state.OrderContext ctx) {
        Order order = ctx.getOrder();
        order.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());

        if (order.getPaymentMethod() == com.fooddelivery.common.enums.PaymentMethod.COD) {
            // What the rider says they took. Recorded on the order whether or not it matches the
            // total, so a short collection is a fact somebody can query rather than a silent gap.
            java.math.BigDecimal declared = readDeclaredCash(ctx);
            order.setCashCollectedAmount(declared);
            saveOrder(order);
            ctx.getLedgerBookkeeper().bookDelivered(order);
            ctx.getLedgerBookkeeper().bookCashCollected(order, declared);
            // COLLECTED, not SUCCESS: it distinguishes cash the rider has actually taken from a
            // gateway capture, which is what RefundService needs to decide whether a COD refund
            // moves money at all.
            order.setPaymentStatus(com.fooddelivery.common.constants.PaymentIntentStatus.COLLECTED);
            updatePaymentIntentStatus(order.getId(), com.fooddelivery.common.constants.PaymentIntentStatus.COLLECTED);
        } else {
            saveOrder(order);
            ctx.getLedgerBookkeeper().bookDelivered(order);
        }

        sendNotification(order.getId().toString(), order.getCustomerId(), com.fooddelivery.common.constants.NotificationTemplate.ORDER_DELIVERED);
    }

    /** The rider's declared cash from the ORDER_DELIVERED payload, or null if they sent none. */
    private java.math.BigDecimal readDeclaredCash(com.fooddelivery.order.service.state.OrderContext ctx) {
        Object payload = ctx.getEventPayload();
        String amountStr = null;
        if (payload instanceof com.fooddelivery.common.event.DeliveredEvent) {
            amountStr = ((com.fooddelivery.common.event.DeliveredEvent) payload).getCashCollectedAmount();
        }
        
        if (amountStr == null) {
            throw new IllegalStateException("Order " + ctx.getOrder().getId() + " was delivered with no declared cash amount");
        }
        try {
            return new java.math.BigDecimal(amountStr);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Order " + ctx.getOrder().getId()
                    + " was delivered with an unreadable cash amount: " + amountStr, e);
        }
    }
}
