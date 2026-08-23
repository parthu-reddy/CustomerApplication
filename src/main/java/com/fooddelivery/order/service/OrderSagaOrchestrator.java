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
import org.springframework.messaging.handler.annotation.Payload;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Service
@lombok.extern.slf4j.Slf4j
public class OrderSagaOrchestrator {
    

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

    private final com.fooddelivery.common.client.PaymentServiceClient paymentClient;
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

    // NOTE: this method PUBLISHES; it is not a Kafka listener. It previously carried a
    // @RetryableTopic annotation and a comment claiming it listened to payment-events -- both were
    // dead: @RetryableTopic only takes effect on a @KafkaListener method, and this class has none.
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
            orderRefundService.processRefund(orderToRefund);
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

        @java.lang.Override
        
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

        
        protected boolean canEqual(final java.lang.Object other) {
            return other instanceof OrderSagaOrchestrator.WebhookPayloadDTO;
        }

        @java.lang.Override
        
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

        
        public PayloadData() {
        }

        
        public PaymentData getPayment() {
            return this.payment;
        }

        
        public void setPayment(final PaymentData payment) {
            this.payment = payment;
        }

        @java.lang.Override
        
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

        
        protected boolean canEqual(final java.lang.Object other) {
            return other instanceof OrderSagaOrchestrator.PayloadData;
        }

        @java.lang.Override
        
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final java.lang.Object $payment = this.getPayment();
            result = result * PRIME + ($payment == null ? 43 : $payment.hashCode());
            return result;
        }

        @java.lang.Override
        
        public java.lang.String toString() {
            return "OrderSagaOrchestrator.PayloadData(payment=" + this.getPayment() + ")";
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

        @java.lang.Override
        
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

        
        protected boolean canEqual(final java.lang.Object other) {
            return other instanceof OrderSagaOrchestrator.PaymentData;
        }

        @java.lang.Override
        
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final java.lang.Object $entity = this.getEntity();
            result = result * PRIME + ($entity == null ? 43 : $entity.hashCode());
            return result;
        }

        @java.lang.Override
        
        public java.lang.String toString() {
            return "OrderSagaOrchestrator.PaymentData(entity=" + this.getEntity() + ")";
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

        @java.lang.Override
        
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

        
        protected boolean canEqual(final java.lang.Object other) {
            return other instanceof OrderSagaOrchestrator.PaymentEntity;
        }

        @java.lang.Override
        
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

    
    private final OrderRefundService orderRefundService;

    public OrderSagaOrchestrator(final IOrderRepository orderRepository, final OutboxEventRepository outboxEventRepository, final IPaymentIntentRepository paymentIntentRepository, final ObjectMapper objectMapper, final KafkaTemplate<String, String> kafkaTemplate, final com.fooddelivery.common.client.PaymentServiceClient paymentClient, final org.springframework.transaction.support.TransactionTemplate transactionTemplate, final com.fooddelivery.order.service.state.OrderActionService orderActionService, final StringRedisTemplate redisTemplate, final OrderRefundService orderRefundService) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.paymentClient = paymentClient;
        this.transactionTemplate = transactionTemplate;
        this.orderActionService = orderActionService;
        this.redisTemplate = redisTemplate;
        this.orderRefundService = orderRefundService;
    }
}
