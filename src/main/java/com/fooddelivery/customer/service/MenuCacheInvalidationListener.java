package com.fooddelivery.customer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.KafkaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
public class MenuCacheInvalidationListener {

    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public MenuCacheInvalidationListener(CacheManager cacheManager, ObjectMapper objectMapper) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaConstants.TOPIC_MENU_EVENTS, groupId = KafkaConstants.GROUP_FOOD_DELIVERY)
    public void onMenuUpdateEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("brandId") && node.has("type") && "MENU_UPDATED".equals(node.get("type").asText())) {
                String brandIdStr = node.get("brandId").asText();
                UUID brandId = UUID.fromString(brandIdStr);
                log.info("Received MENU_UPDATED event for brandId: {}", brandId);
                
                // Clear the cache for the entire brand's outlets if they're stored by brand
                // Or clear everything related to the brand's menus
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
            log.error("Failed to process menu update event", e);
        }
    }
}
