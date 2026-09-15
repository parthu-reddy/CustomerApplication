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
import org.springframework.messaging.handler.annotation.Payload;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class OrderSagaOrchestrator {
    

    private static final String REFUND_TX_PREFIX = "REFUND_";
    private static final String FIELD_ORDER_ID = "orderId";
    private static final String FIELD_GATEWAY_ORDER_ID = "gatewayOrderId";
    private static final String FIELD_EVENT_TYPE = "eventType";
    private static final String FIELD_FAILURE_REASON = "failureReason";
    private final IOrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final com.fooddelivery.order.service.state.OrderActionService orderActionService;
    private final com.fooddelivery.order.ledger.LedgerBookkeeper ledgerBookkeeper;
    private final com.fooddelivery.order.refund.RefundService refundService;

    @Transactional
    public Order startOrderSaga(Order order) {
        java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        String otp = String.format("%06d", secureRandom.nextInt(1000000));
        order.setPickupOtp(otp);
        log.info("Generated pickup OTP for order {}", order.getId());
        // Generate delivery OTP
        String deliveryOtp = String.format("%06d", secureRandom.nextInt(1000000));
        order.setOtp(deliveryOtp);
        log.info("Generated delivery OTP for order {}", order.getId());
        Order savedOrder = orderRepository.save(order);
        OrderCreatedEvent event = OrderCreatedEvent.builder().orderId(savedOrder.getId()).customerId(savedOrder.getCustomerId()).restaurantId(savedOrder.getRestaurantId()).totalAmount(savedOrder.getTotalAmount()).deliveryLat(savedOrder.getDeliveryLat()).deliveryLng(savedOrder.getDeliveryLng()).deliveryAddress(savedOrder.getDeliveryAddress()).pickupOtp(savedOrder.getPickupOtp()).deliveryOtp(savedOrder.getOtp()).build();
        try {
            log.info("Creating ORDER_CREATED outbox event with both OTPs set for order {}", savedOrder.getId());
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

    // NOTE: this method PUBLISHES; it is not a Kafka listener. It previously carried a
    // @RetryableTopic annotation and a comment claiming it listened to payment-events -- both were
    // dead: @RetryableTopic only takes effect on a @KafkaListener method, and this class has none.
    @Transactional
    public void publishDelayApprovalEvent(Order order, boolean approved, String reason) {
        try {
            String eventType = approved ? EventType.ORDER_DELAY_APPROVED.name() : EventType.ORDER_DELAY_REJECTED.name();
            Object event;
            if (approved) {
                event = com.fooddelivery.common.event.OrderDelayApprovedEvent.builder()
                        .orderId(order.getId())
                        .build();
            } else {
                event = com.fooddelivery.common.event.OrderDelayRejectedEvent.builder()
                        .orderId(order.getId().toString())
                        .restaurantId(order.getRestaurantId().toString())
                        .build();
            }
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER).aggregateId(order.getId().toString()).eventType(com.fooddelivery.common.constants.EventType.valueOf(eventType)).payload(objectMapper.writeValueAsString(event)).createdAt(LocalDateTime.now()).build();
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
            com.fooddelivery.order.service.state.OrderContext context = new com.fooddelivery.order.service.state.OrderContext(
                        // No event: this is a local cancellation, not a reaction to one. It passed
                        // an empty ObjectNode purely because the field used to be Object; no state
                        // reached from cancelByCustomer reads the payload (verified across every
                        // OrderState implementation).
                        dbOrder, null, orderActionService, ledgerBookkeeper,
                        null /* no gateway: local cancellation books no capture */,
                        dbOrder.getPaymentMethod());
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
            com.fooddelivery.order.refund.RefundCommand cmd = com.fooddelivery.order.refund.RefundCommand.builder()
               .orderId(orderToRefund.getId())
               .amount(orderToRefund.getTotalAmount())
               .faultType(com.fooddelivery.order.enums.FaultType.UNKNOWN)
               // No destination: RefundService routes it from the payment method and intent state.
               .initiatorType(com.fooddelivery.order.enums.InitiatorType.SYSTEM)
               .reasonCode("SAGA_COMPENSATION")
               .idempotencyKey("saga_" + orderToRefund.getId())
               .build();
            refundService.request(cmd);
        }
    }


    public static class WebhookPayloadDTO {
        private String event;
        private PayloadData payload;

        
        public WebhookPayloadDTO() {
        }

        
        public String getEvent() {
            return this.event;
        }

        
        public PayloadData getPayload() {
            return this.payload;
        }

        
        public void setEvent(final String event) {
            this.event = event;
        }

        
        public void setPayload(final PayloadData payload) {
            this.payload = payload;
        }

    }

    public static class PayloadData {
        private PaymentData payment;

        
        public PayloadData() {
        }

        
        public PaymentData getPayment() {
            return this.payment;
        }

        
        public void setPayment(final PaymentData payment) {
            this.payment = payment;
        }

    }


    public static class PaymentData {
        private PaymentEntity entity;

        
        public PaymentData() {
        }

        
        public PaymentEntity getEntity() {
            return this.entity;
        }

        
        public void setEntity(final PaymentEntity entity) {
            this.entity = entity;
        }

    }


    public static class PaymentEntity {
        @com.fasterxml.jackson.annotation.JsonProperty("order_id")
        private String orderId;
        private String status;
        private double amount;

        
        public PaymentEntity() {
        }

        
        public String getOrderId() {
            return this.orderId;
        }

        
        public String getStatus() {
            return this.status;
        }

        
        public double getAmount() {
            return this.amount;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("order_id")
        
        public void setOrderId(final String orderId) {
            this.orderId = orderId;
        }

        
        public void setStatus(final String status) {
            this.status = status;
        }

        
        public void setAmount(final double amount) {
            this.amount = amount;
        }

    }


}
// @Getter
