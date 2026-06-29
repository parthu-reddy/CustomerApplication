package com.fooddelivery.delivery.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsDispatchService {

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private static final String DRIVER_LOCATION_KEY = "driver_locations";
    private static final String DRIVER_LOCK_PREFIX = "driver_lock:";

    public String dispatchNearestDriver(double restaurantLat, double restaurantLng, UUID orderId) {
        log.info("Dispatching nearest driver for order {}", orderId);
        
        // 1. Find drivers within 5km
        Circle circle = new Circle(new Point(restaurantLng, restaurantLat), new Distance(5, org.springframework.data.geo.Metrics.KILOMETERS));
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().radius(DRIVER_LOCATION_KEY, circle);
        
        if (results == null || results.getContent().isEmpty()) {
            log.warn("No drivers found within 5km of restaurant");
            return null;
        }

        // Collect available drivers and simulate Distance Matrix ETA sorting
        List<String> availableDrivers = new ArrayList<>();
        for (var result : results.getContent()) {
            availableDrivers.add(result.getContent().getName());
        }
        
        // Simulating Ola Maps Distance Matrix API call
        availableDrivers = mockDistanceMatrixSort(availableDrivers, restaurantLat, restaurantLng);

        // 2. Iterate and try to acquire lock (atomic)
        for (String driverId : availableDrivers) {
            String lockKey = DRIVER_LOCK_PREFIX + driverId;
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, orderId.toString(), Duration.ofMinutes(15));
            
            if (Boolean.TRUE.equals(locked)) {
                log.info("Driver {} assigned to order {} after ETA sorting", driverId, orderId);
                // Also remove driver from available geo index if they are now busy
                redisTemplate.opsForGeo().remove(DRIVER_LOCATION_KEY, driverId);
                return driverId;
            }
        }
        
        log.warn("Drivers found but all were busy for order {}", orderId);
        return null;
    }
    
    @CircuitBreaker(name = "olaMapsRouting", fallbackMethod = "fallbackDistanceSort")
    private List<String> mockDistanceMatrixSort(List<String> driverIds, double lat, double lng) {
        String cacheKey = "distance_matrix:" + lat + ":" + lng;
        
        // Check Redis cache first
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Using cached distance matrix routing");
            return Arrays.asList(cached.split(","));
        }
        
        // In a production scenario, we would use RestTemplate to call Maps Integration API here.
        // For now, we mock a shuffle to simulate dynamic ETA variations.
        List<String> sortedDrivers = new ArrayList<>(driverIds);
        Collections.shuffle(sortedDrivers);
        
        // Cache the result for 30 seconds
        redisTemplate.opsForValue().set(cacheKey, String.join(",", sortedDrivers), Duration.ofSeconds(30));
        
        return sortedDrivers;
    }

    private List<String> fallbackDistanceSort(List<String> driverIds, double lat, double lng, Throwable t) {
        log.warn("Circuit breaker open or failure in routing, using fallback Haversine distance sort", t);
        // Mock fallback logic
        List<String> sortedDrivers = new ArrayList<>(driverIds);
        Collections.reverse(sortedDrivers);
        return sortedDrivers;
    }

    public void releaseDriverLock(String driverId) {
        String lockKey = DRIVER_LOCK_PREFIX + driverId;
        redisTemplate.delete(lockKey);
        log.info("Released driver lock for driver {}", driverId);
        // Note: Realistically, you would also put the driver back into the Geo index,
        // but we'll assume the driver's next telemetry ping puts them back in.
    }
}
