package com.fooddelivery.customer.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@EmbeddedKafka(partitions = 1, topics = {"customer-events", "customer-events.DLT"})
@Import({KafkaResilienceIntegrationTest.TestConsumer.class, com.fooddelivery.common.config.KafkaConfig.class})
public class KafkaResilienceIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TestConsumer testConsumer;

    @Test
    public void testIdempotency() throws InterruptedException {
        String eventPayload = "{\"orderId\": \"123\", \"status\": \"CREATED\", \"idempotencyKey\": \"key-1\"}";
        
        // Publish identical payload three times (Chaos Testing)
        kafkaTemplate.send("customer-events", eventPayload);
        kafkaTemplate.send("customer-events", eventPayload);
        kafkaTemplate.send("customer-events", eventPayload);
        kafkaTemplate.flush();

        // Wait for processing
        testConsumer.latch.await(30, TimeUnit.SECONDS);

        // Should only be processed once due to idempotency check
        assertEquals(1, testConsumer.processedCount.get());
    }

    @Test
    public void testPoisonPillRouting() throws InterruptedException {
        String malformedPayload = "{ bad_json_missing_quotes }";

        kafkaTemplate.send("customer-events", malformedPayload);

        // Wait for DLQ routing
        boolean routed = testConsumer.dlqLatch.await(15, TimeUnit.SECONDS);
        
        assertTrue(routed, "Poison pill message should have been routed to DLQ topic");
    }



    @Component
    public static class TestConsumer extends com.fooddelivery.common.messaging.BaseIdempotentConsumer<String> {
        public final CountDownLatch latch = new CountDownLatch(1);
        public final CountDownLatch dlqLatch = new CountDownLatch(1);
        public final AtomicInteger processedCount = new AtomicInteger(0);

        @Autowired
        public TestConsumer(KafkaTemplate<String, String> template) {
            super(template);
        }

        @KafkaListener(topics = "customer-events", groupId = "test-group")
        public void consume(String payload) {
            if (payload.contains("bad_json")) {
                throw new RuntimeException("Malformed JSON");
            }
            
            String idempotencyKey = "key-1"; // Extract from payload in real scenario
            processSafely(payload, idempotencyKey, "customer-events", payload, data -> {
                processedCount.incrementAndGet();
                latch.countDown();
                return true;
            });
        }

        @KafkaListener(topics = "customer-events.DLT", groupId = "test-dlq-group")
        public void consumeDlq(String payload) {
            dlqLatch.countDown();
        }

        @Override
        protected boolean isDuplicate(String idempotencyKey) {
            return processedCount.get() > 0;
        }

        @Override
        protected void markAsProcessed(String idempotencyKey) {
            // Already handled in processor
        }
    }
}
