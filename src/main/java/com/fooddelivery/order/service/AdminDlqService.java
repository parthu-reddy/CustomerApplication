package com.fooddelivery.order.service;

import com.fooddelivery.common.constants.KafkaConstants;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.repository.RefundRepository;
import com.fooddelivery.order.refund.RefundService;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminDlqService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RefundRepository refundRepository;
    private final RefundService refundService;

    public void retryDlqEvent(Map<String, Object> payload, String topic) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String jsonPayload = mapper.writeValueAsString(payload);
        String targetTopic = topic != null && !topic.isEmpty() ? topic : KafkaConstants.TOPIC_ORDER_EVENTS;
        log.info("Admin manually retrying DLQ event to topic {}: {}", targetTopic, jsonPayload);
        
        String partitionKey = null;
        if (payload.containsKey("aggregateId") && payload.get("aggregateId") != null) {
            partitionKey = payload.get("aggregateId").toString();
        } else if (payload.containsKey("payload") && payload.get("payload") instanceof Map) {
            Map<String, Object> innerPayload = (Map<String, Object>) payload.get("payload");
            if (innerPayload.containsKey("orderId") && innerPayload.get("orderId") != null) {
                partitionKey = innerPayload.get("orderId").toString();
            }
        }
        
        MessageBuilder<String> builder = MessageBuilder
            .withPayload(jsonPayload)
            .setHeader(KafkaHeaders.TOPIC, targetTopic);

        if (partitionKey != null) {
            builder.setHeader(KafkaHeaders.KEY, partitionKey);
        }
        
        if (payload.containsKey("eventId") && payload.get("eventId") != null) {
            builder.setHeader("eventId", payload.get("eventId").toString());
        } else {
            builder.setHeader("eventId", UUID.randomUUID().toString());
        }
        
        kafkaTemplate.send(builder.build());
    }

    public void retryRefund(UUID refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found for ID: " + refundId));
        
        if (refund.getStatus() != com.fooddelivery.common.enums.RefundStatus.FAILED) {
            throw new IllegalArgumentException("Refund can only be retried if status is FAILED. Current status: " + refund.getStatus());
        }
        
        log.info("Admin manually retrying refund for ID: {}", refundId);
        
        refund.setStatus(com.fooddelivery.common.enums.RefundStatus.PROCESSING);
        refund.setFailureReason(null);
        refundRepository.save(refund);
        
        refundService.retryStuck();
    }
}
