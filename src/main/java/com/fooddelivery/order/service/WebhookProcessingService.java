package com.fooddelivery.order.service;

import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.order.entity.WebhookDelivery;
import com.fooddelivery.order.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookProcessingService {

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9]{10,14}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    public void processAndStoreWebhook(String provider, String payload) {
        String maskedPayload = maskPii(payload);
        
        WebhookDelivery delivery = WebhookDelivery.builder()
                .id(UUID.randomUUID())
                .provider(provider)
                .maskedPayload(maskedPayload)
                .build();
                
        webhookDeliveryRepository.save(delivery);
        log.info("Persisted masked webhook payload to audit log for provider {}", provider);
        
        kafkaTemplate.send(KafkaConstants.TOPIC_PAYMENT_EVENTS, UUID.randomUUID().toString(), payload);
        log.info("Published original payment webhook payload to Kafka topic {}", KafkaConstants.TOPIC_PAYMENT_EVENTS);
    }

    private String maskPii(String payload) {
        String result = payload;
        result = PHONE_PATTERN.matcher(result).replaceAll("XXX-XXX-XXXX");
        result = EMAIL_PATTERN.matcher(result).replaceAll("XXX@XXX.com");
        return result;
    }
}
