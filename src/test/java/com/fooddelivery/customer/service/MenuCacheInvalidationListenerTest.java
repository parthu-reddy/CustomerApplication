package com.fooddelivery.customer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.event.EventBinder;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.customer.client.RestaurantClient;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuCacheInvalidationListenerTest {

    /**
     * The listener cleared "restaurantDetails", "brandOutlets" and "menuItemsBatch" while
     * RestaurantClient caches under "customer-app:"-prefixed names. A Redis cache manager hands
     * out a fresh cache for any name, so the clears hit nothing and nothing failed. This ties the
     * two sets of names together by reading the real @Cacheable values.
     */
    @Test
    void menuUpdatedClearsTheCachesRestaurantClientActuallyWrites() throws Exception {
        CacheManager cacheManager = mock(CacheManager.class);
        Set<String> cleared = new HashSet<>();
        when(cacheManager.getCache(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            Cache cache = mock(Cache.class);
            org.mockito.Mockito.doAnswer(i -> cleared.add(name)).when(cache).clear();
            return cache;
        });
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        ObjectMapper mapper = new ObjectMapper();
        MenuCacheInvalidationListener listener = new MenuCacheInvalidationListener(
                cacheManager, mapper, mock(IIdempotencyKeyRepository.class), tx,
                new EventBinder(mapper, jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()));

        listener.onMenuUpdateEvent(
                mapper.writeValueAsString(Map.of("brandId", UUID.randomUUID().toString(), "type", "MENU_UPDATED")),
                Map.of("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));

        for (String method : new String[] {"getRestaurantById", "getBrandOutlets", "getMenuItemsBatch"}) {
            String cacheName = Arrays.stream(RestaurantClient.class.getMethods())
                    .filter(m -> m.getName().equals(method))
                    .findFirst().orElseThrow()
                    .getAnnotation(Cacheable.class).value()[0];
            assertTrue(cleared.contains(cacheName), "MENU_UPDATED did not clear " + cacheName + "; cleared " + cleared);
        }
    }
}
