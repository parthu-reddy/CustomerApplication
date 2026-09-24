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
@lombok.RequiredArgsConstructor
public class MenuCacheInvalidationListener {

    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final IIdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionTemplate transactionTemplate;
    private final com.fooddelivery.common.event.EventBinder eventBinder;


    @KafkaListener(topics = KafkaConstants.TOPIC_MENU_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY + "-menucacheinvalidationlistener")
    public void onMenuUpdateEvent(String payload, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        try {
            String extractedEventId = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
            if (extractedEventId == null) {
                throw new IllegalArgumentException("Missing eventId header");
            }
            final String resolvedEventId = extractedEventId;

            String idempotencyKeyStr = "processed_event:menucache:" + resolvedEventId;

            transactionTemplate.execute(status -> {
                if (idempotencyKeyRepository.existsById(idempotencyKeyStr)) {
                    log.info("Duplicate event ignored for cache invalidation: {}", idempotencyKeyStr);
                    return null;
                }
                idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKeyStr));

                try {
                    // brandId is NOT @NotNull on the event: this listener deliberately tolerates
                    // other menu-events shapes and acts only on MENU_UPDATED, so a missing brandId
                    // means "not mine", not "reject".
                    com.fooddelivery.common.event.MenuCacheInvalidationEvent event =
                            eventBinder.bind(payload, com.fooddelivery.common.event.MenuCacheInvalidationEvent.class);
                    if (event.getBrandId() != null && "MENU_UPDATED".equals(event.getType())) {
                        UUID brandId = UUID.fromString(event.getBrandId());
                        log.info("Received MENU_UPDATED event for brandId: {}", brandId);
                        
                        // The names RestaurantClient's @Cacheable uses. These read "brandOutlets" etc.
                        // before: getCache() on a Redis cache manager creates a cache for any name,
                        // so each call returned an empty new cache, cleared it, and the real entries
                        // lived out their TTL -- menu and outlet edits reached customers up to 10+
                        // minutes late.
                        org.springframework.cache.Cache brandOutletsCache = cacheManager.getCache("customer-app:brandOutlets");
                        if (brandOutletsCache != null) {
                            brandOutletsCache.clear();
                        }
                        
                        org.springframework.cache.Cache restaurantDetailsCache = cacheManager.getCache("customer-app:restaurantDetails");
                        if (restaurantDetailsCache != null) {
                            restaurantDetailsCache.clear();
                        }

                        org.springframework.cache.Cache menuItemsBatchCache = cacheManager.getCache("customer-app:menuItemsBatch");
                        if (menuItemsBatchCache != null) {
                            menuItemsBatchCache.clear();
                        }
                    }                } catch (Exception e) {
                    throw new RuntimeException("Failed to process menu update event", e);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to process menu update event", e);
        }
    }
}
