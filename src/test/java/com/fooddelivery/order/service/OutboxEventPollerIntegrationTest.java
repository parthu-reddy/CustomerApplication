package com.fooddelivery.order.service;

import com.fooddelivery.common.test.BaseIntegrationTest;
import com.fooddelivery.order.entity.OutboxEventEntity;
import com.fooddelivery.order.repository.IOutboxEventRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

class OutboxEventPollerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IOutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventPoller outboxEventPoller;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void shouldPollUnprocessedEventAndPublishToKafka() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId("ORDER123")
                .eventType("OrderCreated")
                .payload("{\"orderId\":\"ORDER123\"}")
                .status("UNPROCESSED")
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .build();
        
        outboxEventRepository.save(entity);

        // Act - Trigger poller manually
        outboxEventPoller.pollOutboxEvents();

        // Assert - DB status updated
        OutboxEventEntity updated = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("PROCESSED");

    }
}
