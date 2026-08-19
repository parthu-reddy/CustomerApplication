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

@SpringBootTest(classes = BaseMessagingClass.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureMessageVerifier
@EmbeddedKafka(partitions = 1, topics = {"order-events", "chat-events", "wallet-events", "ledger-events", "ad-events", "platform.notifications.dispatch"})
public abstract class BaseMessagingClass {

    @org.springframework.boot.test.context.TestConfiguration
    
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

    public void fireOrderCreated() {
        String payload = """
{
  "eventId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "type": "ORDER_CREATED",
  "payload": {
    "orderId": 1001,
    "customerId": "user-123",
    "restaurantId": 501,
    "totalAmount": 15.50
  }
}""";
        kafkaTemplate.send("order-events", payload);
    }
    public void fireChatEvent() {
        String payload = """
{
  "eventId": "chat-444",
  "type": "CHAT_MESSAGE_SENT",
  "payload": {
    "chatId": "chat-100",
    "senderId": "user-123",
    "message": "Where is my food?"
  }
}""";
        kafkaTemplate.send("chat-events", payload);
    }
    public void fireWalletEvent() {
        String payload = """
{
  "eventId": "wal-111",
  "type": "WALLET_DEBITED",
  "payload": {
    "userId": "user-123",
    "amount": 15.50
  }
}""";
        kafkaTemplate.send("wallet-events", payload);
    }
    public void fireLedgerEvent() {
        String payload = """
{
  "eventId": "led-222",
  "type": "LEDGER_ENTRY_CREATED",
  "payload": {
    "transactionId": "txn-999",
    "amount": 15.50
  }
}""";
        kafkaTemplate.send("ledger-events", payload);
    }
    public void fireAdEvent() {
        String payload = """
{
  "eventId": "ad-333",
  "type": "AD_DISPLAYED",
  "payload": {
    "campaignId": 999,
    "userId": "user-123"
  }
}""";
        kafkaTemplate.send("ad-events", payload);
    }
    public void fireNotificationDispatch() {
        String payload = """
{
  "eventId": "not-444",
  "type": "NOTIFICATION_SENT",
  "payload": {
    "userId": "user-123",
    "message": "Your order is confirmed."
  }
}""";
        kafkaTemplate.send("platform.notifications.dispatch", payload);
    }

}
