package com.fooddelivery.customer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.entity.IdempotencyKey;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
public class MenuCacheInvalidationListener {

    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final IIdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionTemplate transactionTemplate;

    public MenuCacheInvalidationListener(CacheManager cacheManager, ObjectMapper objectMapper, IIdempotencyKeyRepository idempotencyKeyRepository, TransactionTemplate transactionTemplate) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @KafkaListener(topics = KafkaConstants.TOPIC_MENU_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY)
    public void onMenuUpdateEvent(String payload, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        try {
            String extractedEventId = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
            final String resolvedEventId;
            if (extractedEventId == null) {
                resolvedEventId = UUID.nameUUIDFromBytes(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            } else {
                resolvedEventId = extractedEventId;
            }

            String idempotencyKeyStr = "processed_event:menucache:" + resolvedEventId;

            transactionTemplate.execute(status -> {
                if (idempotencyKeyRepository.existsById(idempotencyKeyStr)) {
                    log.info("Duplicate event ignored for cache invalidation: {}", idempotencyKeyStr);
                    return null;
                }
                idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKeyStr));

                try {
                    JsonNode node = objectMapper.readTree(payload);
                    if (node.has("brandId") && node.has("type") && "MENU_UPDATED".equals(node.get("type").asText())) {
                        String brandIdStr = node.get("brandId").asText();
                        UUID brandId = UUID.fromString(brandIdStr);
                        log.info("Received MENU_UPDATED event for brandId: {}", brandId);
                        
                        org.springframework.cache.Cache brandOutletsCache = cacheManager.getCache("brandOutlets");
                        if (brandOutletsCache != null) {
                            brandOutletsCache.clear();
                        }
                        
                        org.springframework.cache.Cache restaurantDetailsCache = cacheManager.getCache("restaurantDetails");
                        if (restaurantDetailsCache != null) {
                            restaurantDetailsCache.clear();
                        }

                        org.springframework.cache.Cache menuItemsBatchCache = cacheManager.getCache("menuItemsBatch");
                        if (menuItemsBatchCache != null) {
                            menuItemsBatchCache.clear();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process menu update event", e);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to process menu update event", e);
        }
    }
}
