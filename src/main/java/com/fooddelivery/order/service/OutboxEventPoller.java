package com.fooddelivery.order.service;

import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.order.entity.OutboxEventEntity;
import com.fooddelivery.order.repository.IOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPoller {

    private final IOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.poll.interval:5000}")
    @Transactional
    public void pollOutboxEvents() {
        // Fetch up to 100 unprocessed events directly using the repository query with FOR UPDATE SKIP LOCKED
        List<OutboxEventEntity> unprocessedEvents = outboxEventRepository.findUnprocessedEventsAndLock(List.of(com.fooddelivery.common.constants.AppConstants.OUTBOX_STATUS_UNPROCESSED, com.fooddelivery.common.constants.AppConstants.OUTBOX_STATUS_FAILED));

        if (unprocessedEvents.isEmpty()) {
            return; // Nothing to process
        }
        
        log.info("Found {} unprocessed outbox events", unprocessedEvents.size());

        for (OutboxEventEntity event : unprocessedEvents) {
            try {
                // Publish to Kafka based on aggregate type or just order-events
                String topic = KafkaConstants.TOPIC_ORDER_EVENTS;
                if (com.fooddelivery.common.constants.AppConstants.AGGREGATE_PAYMENT.equals(event.getAggregateType())) {
                    topic = KafkaConstants.TOPIC_PAYMENT_EVENTS;
                } else if (com.fooddelivery.common.constants.AppConstants.AGGREGATE_NOTIFICATION.equals(event.getAggregateType())) {
                    topic = KafkaConstants.TOPIC_NOTIFICATIONS_DISPATCH;
                }
                
                org.springframework.messaging.Message<String> message = org.springframework.messaging.support.MessageBuilder
                        .withPayload(event.getPayload())
                        .setHeader(org.springframework.kafka.support.KafkaHeaders.TOPIC, topic)
                        .setHeader(org.springframework.kafka.support.KafkaHeaders.KEY, event.getAggregateId())
                        .setHeader("eventType", event.getEventType())
                        .build();
                        
                kafkaTemplate.send(message).get(3, java.util.concurrent.TimeUnit.SECONDS);
                
                // Mark as processed
                event.setStatus(com.fooddelivery.common.constants.AppConstants.OUTBOX_STATUS_PROCESSED);
                event.setProcessedAt(LocalDateTime.now());
                log.info("Successfully published outbox event {} to topic {}", event.getId(), topic);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}", event.getId(), e);
                event.setStatus(com.fooddelivery.common.constants.AppConstants.OUTBOX_STATUS_FAILED);
                event.setErrorMessage(e.getMessage());
            }
        }
        
        outboxEventRepository.saveAll(unprocessedEvents);
    }
}
