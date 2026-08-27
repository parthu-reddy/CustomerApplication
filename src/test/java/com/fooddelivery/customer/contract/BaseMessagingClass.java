package com.fooddelivery.customer.contract;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Bean;

@SpringBootTest(classes = BaseMessagingClass.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"})
@org.springframework.test.context.ActiveProfiles("contract-test")
@AutoConfigureMessageVerifier
@EmbeddedKafka(partitions = 1, topics = {"order-events", "payment-events", "chat-events", "wallet-events", "ledger-events", "platform.notifications.dispatch"})
public abstract class BaseMessagingClass {

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfig {
        @Bean
        public KafkaMessageVerifier kafkaMessageVerifier() {
            return new KafkaMessageVerifier();
        }
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers", "localhost:9092"));
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /** The same auto-configured mapper OrderSagaOrchestrator is injected with. */
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Publishes ORDER_CREATED through the real production path rather than a hand-written JSON
     * blob: a real OrderCreatedEvent is serialized exactly as OrderSagaOrchestrator serializes it,
     * wrapped in a real OutboxEventEntity, and handed to the real OutboxProcessor -- which decides
     * the topic from the aggregate type and the Kafka key from the aggregate id.
     *
     * Only the repository is mocked; persistence is not part of the contract. This means the test
     * now breaks if a field is added to or renamed on OrderCreatedEvent, or if the ORDER ->
     * order-events topic routing changes. The previous text-block version could not detect either.
     */
    public void fireOrderCreated() throws Exception {
        com.fooddelivery.common.event.OrderCreatedEvent event =
                com.fooddelivery.common.event.OrderCreatedEvent.builder()
                        .orderId(java.util.UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301"))
                        .customerId(java.util.UUID.fromString("6b1d3c22-9f45-4a7e-8c11-2d4e6f8a9b02"))
                        .restaurantId(java.util.UUID.fromString("9c8b7a65-1e2d-4f30-b5a6-7c8d9e0f1a23"))
                        .totalAmount(new java.math.BigDecimal("15.50"))
                        .deliveryLat(12.971598)
                        .deliveryLng(77.594562)
                        .deliveryAddress("221B Baker Street, Bangalore")
                        .pickupOtp("1234")
                        .deliveryOtp("5678")
                        .build();

        com.fooddelivery.common.outbox.entity.OutboxEventEntity outboxEvent =
                com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder()
                        .id(java.util.UUID.randomUUID())
                        .aggregateType(com.fooddelivery.common.constants.AggregateType.ORDER)
                        .aggregateId(event.getOrderId().toString())
                        .eventType(com.fooddelivery.common.constants.EventType.ORDER_CREATED)
                        .payload(objectMapper.writeValueAsString(event))
                        .createdAt(java.time.LocalDateTime.now())
                        .build();

        com.fooddelivery.common.outbox.repository.OutboxEventRepository outboxRepository =
                org.mockito.Mockito.mock(com.fooddelivery.common.outbox.repository.OutboxEventRepository.class);
        org.mockito.Mockito.when(outboxRepository.findTop100ByStatusInOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new java.util.ArrayList<>(java.util.List.of(outboxEvent)));

        new com.fooddelivery.common.outbox.service.OutboxProcessor(outboxRepository, kafkaTemplate, new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                .processOutboxEvents();
    }
    /** Mirrors ChatRefundProcessorService's CHAT_REFUND_QUOTE_RESPONSE map. */
    public void fireChatEvent() throws Exception {
        java.util.Map<String, Object> responseMap = new java.util.HashMap<>();
        responseMap.put("quoteAmount", new java.math.BigDecimal("125.50"));
        responseMap.put("refundType", "PARTIAL");
        publishViaOutbox(com.fooddelivery.common.constants.AggregateType.CHAT_SESSION,
                "7a1d5e90-3c22-4b6f-8a11-9d4c2e77b501",
                com.fooddelivery.common.constants.EventType.CHAT_REFUND_QUOTE_RESPONSE, responseMap);
    }
    /** Mirrors OrderActionService.emitEarningsGeneratedEvent - FLAT, no body eventType. */
    public void fireWalletEarnings() throws Exception {
        String entityId = "9c8b7a65-1e2d-4f30-b5a6-7c8d9e0f1a23";
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("entityId", entityId);
        payload.put("entityType", "RESTAURANT");
        payload.put("amount", new java.math.BigDecimal("125.50").toString());
        payload.put("referenceId", "ORDER_3f2504e0-4f89-41d3-9a0c-0305e82c3301");
        payload.put("description", "Earnings for Order 3f2504e0-4f89-41d3-9a0c-0305e82c3301");
        payload.put("metadata", "{}");
        publishViaOutbox(com.fooddelivery.common.constants.AggregateType.WALLET, entityId,
                com.fooddelivery.common.constants.EventType.EARNINGS_GENERATED, payload);
    }
    /** Mirrors OrderActionService: a real serialized NotificationRequestEvent. */
    public void fireNotificationDispatch() throws Exception {
        java.util.UUID customerId = java.util.UUID.fromString("6b1d3c22-9f45-4a7e-8c11-2d4e6f8a9b02");
        com.fooddelivery.common.event.NotificationRequestEvent notificationEvent =
                com.fooddelivery.common.event.NotificationRequestEvent.builder()
                        .userId(customerId)
                        .channel(com.fooddelivery.common.enums.ChannelType.PUSH)
                        .eventName("ORDER_CONFIRMED")
                        .templateParams(java.util.List.of("3f2504e0-4f89-41d3-9a0c-0305e82c3301"))
                        .build();
        publishViaOutbox(com.fooddelivery.common.constants.AggregateType.NOTIFICATION,
                customerId.toString(),
                com.fooddelivery.common.constants.EventType.NOTIFICATION_REQUEST, notificationEvent);
    }


    /** Drives the real OutboxProcessor: real topic routing, real key, real eventType header. */
    /** Mirrors OrderActionService's LEDGER_TRANSACTION_REQUEST ObjectNode (amount as String). */
    public void fireLedgerEvent() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode n = objectMapper.createObjectNode();
        String transferId = "0f1a5cb3-2b6d-5a1e-9c47-8e3f6d2a1b04";
        n.put("transferId", transferId);
        n.put("referenceId", "3f2504e0-4f89-41d3-9a0c-0305e82c3301");
        n.put("fromId", "6b1d3c22-9f45-4a7e-8c11-2d4e6f8a9b02");
        n.put("fromType", com.fooddelivery.common.enums.AccountType.PLATFORM.name());
        n.put("toId", "9c8b7a65-1e2d-4f30-b5a6-7c8d9e0f1a23");
        n.put("toType", com.fooddelivery.common.enums.AccountType.RESTAURANT.name());
        n.put("amount", new java.math.BigDecimal("125.50").toString());
        n.put("chargeCategory", com.fooddelivery.common.enums.ChargeCategory.FOOD_COST.name());
        publishViaOutbox(com.fooddelivery.common.constants.AggregateType.LEDGER, transferId,
                com.fooddelivery.common.constants.EventType.LEDGER_TRANSACTION_REQUEST, n);
    }

    /** Mirrors AdminOrderManualController - flat payload, body eventType differs from the header. */
    public void fireWalletReversal() throws Exception {
        String orderId = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";
        java.util.Map<String, Object> eventPayload = new java.util.HashMap<>();
        eventPayload.put("entityId", "9c8b7a65-1e2d-4f30-b5a6-7c8d9e0f1a23");
        eventPayload.put("entityType", "RESTAURANT");
        eventPayload.put("amount", new java.math.BigDecimal("40.00").toString());
        eventPayload.put("referenceId", "REV_" + orderId + "_1699999999999");
        eventPayload.put("description", "Reversal for order " + orderId);
        eventPayload.put("chargeCategory", com.fooddelivery.common.enums.ChargeCategory.REFUND.name());
        eventPayload.put("eventType", com.fooddelivery.common.constants.EventType.REVERSAL_GENERATED.name());
        publishViaOutbox(com.fooddelivery.common.constants.AggregateType.WALLET, orderId,
                com.fooddelivery.common.constants.EventType.REVERSAL_GENERATED, eventPayload);
    }

    protected void publishViaOutbox(com.fooddelivery.common.constants.AggregateType aggregateType,
                                    String aggregateId,
                                    com.fooddelivery.common.constants.EventType eventType,
                                    Object payloadObject) throws Exception {
        com.fooddelivery.common.outbox.entity.OutboxEventEntity outboxEvent =
                com.fooddelivery.common.outbox.entity.OutboxEventEntity.builder()
                        .id(java.util.UUID.randomUUID())
                        .aggregateType(aggregateType)
                        .aggregateId(aggregateId)
                        .eventType(eventType)
                        .payload(payloadObject instanceof String
                                ? (String) payloadObject
                                : objectMapper.writeValueAsString(payloadObject))
                        .createdAt(java.time.LocalDateTime.now())
                        .build();
        com.fooddelivery.common.outbox.repository.OutboxEventRepository repo =
                org.mockito.Mockito.mock(com.fooddelivery.common.outbox.repository.OutboxEventRepository.class);
        org.mockito.Mockito.when(repo.findTop100ByStatusInOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new java.util.ArrayList<>(java.util.List.of(outboxEvent)));
        new com.fooddelivery.common.outbox.service.OutboxProcessor(repo, kafkaTemplate, new io.micrometer.core.instrument.simple.SimpleMeterRegistry()).processOutboxEvents();
    }

    public void firePaymentRefundRequested() throws Exception {
        java.util.Map<String, Object> payloadMap = new java.util.HashMap<>();
        payloadMap.put("intentId", "3f2504e0-4f89-41d3-9a0c-0305e82c3301");
        payloadMap.put("gatewayOrderId", "pay_12345");
        payloadMap.put("amountInInr", 15.50);
        payloadMap.put("gatewayName", "RAZORPAY");
        payloadMap.put("orderId", "6b1d3c22-9f45-4a7e-8c11-2d4e6f8a9b02");
        payloadMap.put("refundDestination", "GATEWAY");
        publishViaOutbox(com.fooddelivery.common.constants.AggregateType.PAYMENT,
                "6b1d3c22-9f45-4a7e-8c11-2d4e6f8a9b02",
                com.fooddelivery.common.constants.EventType.PAYMENT_REFUND_REQUESTED, payloadMap);
    }

}
