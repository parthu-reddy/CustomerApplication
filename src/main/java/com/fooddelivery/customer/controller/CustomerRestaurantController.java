package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fooddelivery.customer.client.RestaurantClient;
import com.fooddelivery.customer.client.MapsClient;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/restaurants")
@PreAuthorize("hasRole(\'CUSTOMER\')")
public class CustomerRestaurantController {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CustomerRestaurantController.class);
    private final RestaurantClient restaurantClient;
    private final MapsClient mapsClient;
    private final com.fooddelivery.customer.service.DynamicPricingService dynamicPricingService;
    private final com.fooddelivery.customer.config.DynamicPricingConfig dynamicPricingConfig;
    private final com.fooddelivery.customer.repository.CustomerAddressRepository customerAddressRepository;
    private final com.fooddelivery.customer.client.AdvertisementClient advertisementClient;

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<Object>>> getNearbyRestaurants(@RequestParam double lat, @RequestParam double lng, @RequestParam(defaultValue = "5.0") double radius) {
        ApiResponse<List<Object>> response = restaurantClient.getNearbyRestaurants(lat, lng, radius);
        // Fetch ads from AdvertisementService (BiddingEngine)
        try {
            Map<String, Object> bidRequest = new java.util.HashMap<>();
            bidRequest.put("id", java.util.UUID.randomUUID().toString());
            Map<String, Object> imp = new java.util.HashMap<>();
            imp.put("id", "1");
            bidRequest.put("imp", java.util.List.of(imp));
            Map<String, Object> user = new java.util.HashMap<>();
            user.put("geo", "US"); // simplified
            bidRequest.put("user", user);
            Object adResponse = advertisementClient.fetchAds(bidRequest);
            if (adResponse != null && response.getData() != null) {
                // If we get an ad, extract the ad content and inject it at the top of the restaurant list
                // Realistically, adResponse should be mapped properly to extract campaign/restaurant ID.
                // For simplicity, we just add a mock sponsored listing object.
                Map<String, Object> sponsoredListing = new java.util.HashMap<>();
                sponsoredListing.put("isSponsored", true);
                if (adResponse instanceof java.util.List && !((java.util.List<?>) adResponse).isEmpty()) {
                    sponsoredListing.put("adData", ((java.util.List<?>) adResponse).get(0));
                } else {
                    sponsoredListing.put("adData", adResponse);
                }
                // Add to the front of the list
                List<Object> merged = new java.util.ArrayList<>();
                merged.add(sponsoredListing);
                merged.addAll(response.getData());
                return ResponseEntity.ok(ApiResponse.success(merged, "Successfully fetched nearby restaurants"));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch advertisements: {}", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/brands/{brandId}/outlets")
    public ResponseEntity<ApiResponse<List<Object>>> getBrandOutlets(@org.springframework.web.bind.annotation.PathVariable java.util.UUID brandId, @RequestParam double lat, @RequestParam double lng, @RequestParam(defaultValue = "5.0") double radius) {
        return ResponseEntity.ok(restaurantClient.getBrandOutlets(brandId, lat, lng, radius));
    }

    @GetMapping("/{id}/delivery-availability")
    public ResponseEntity<ApiResponse<Boolean>> checkDeliveryAvailability(@org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        // 1. Fetch Restaurant Coordinates
        Map<String, Object> responseBody;
        try {
            responseBody = restaurantClient.getRestaurantById(id);
        } catch (Exception e) {
            throw new IllegalArgumentException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_NOT_FOUND + id);
        }
        if (responseBody == null) {
            throw new IllegalArgumentException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_NOT_FOUND + id);
        }
        Map<String, Object> restaurant = (Map<String, Object>) responseBody.get("data");
        if (restaurant == null) {
            throw new IllegalArgumentException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_NOT_FOUND + id);
        }
        Double lat = (Double) restaurant.get("lat");
        Double lng = (Double) restaurant.get("lng");
        if (lat == null || lng == null) {
            throw new IllegalStateException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_UNKNOWN);
        }
        // 2. Check Driver Availability in MapsIntegration
        try {
            Map<String, Object> mapsResponse = mapsClient.checkFleetAvailability(com.fooddelivery.common.constants.AppConstants.DEFAULT_CITY_ID, lat, lng, com.fooddelivery.common.constants.AppConstants.MAX_DELIVERY_RADIUS_KM);
            if (mapsResponse != null) {
                Boolean available = (Boolean) mapsResponse.get("available");
                if (Boolean.TRUE.equals(available)) {
                    return ResponseEntity.ok(ApiResponse.success(true, "Delivery partner available."));
                }
            }
        } catch (Exception e) {
            // Log and allow it to fall through to the unavailable exception
            log.warn("Failed to reach MapsIntegration for fleet check: {}", e.getMessage());
        }
        // 3. Throw Exception if not available
        throw new com.fooddelivery.customer.exception.DeliveryPartnerUnavailableException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_NO_DELIVERY_PARTNER_NEARBY, com.fooddelivery.common.constants.AppConstants.ERROR_NO_DELIVERY_PARTNER_NEARBY);
    }

    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @GetMapping("/{id}/delivery-pricing")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeliveryPricing(@org.springframework.web.bind.annotation.PathVariable java.util.UUID id, @RequestParam java.util.UUID addressId, java.security.Principal principal) {
        com.fooddelivery.customer.entity.CustomerAddress address = customerAddressRepository.findById(addressId).orElseThrow(() -> new IllegalArgumentException("Address not found"));
        // SECURITY CHECK: Verify address belongs to authenticated customer
        if (principal == null || principal.getName() == null || !address.getCustomerId().toString().equals(principal.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        // RATE LIMITING: 5 requests per minute per user per restaurant using Redis
        String rateLimitKey = "rate_limit:delivery_pricing:" + principal.getName() + ":" + id;
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateLimitKey, java.time.Duration.ofMinutes(1));
        }
        // Anti-pattern fix: If application crashed between increment and expire, 
        // TTL will be -1 (infinite). Set it to 1 min to prevent permanent lockout.
        Long ttl = redisTemplate.getExpire(rateLimitKey);
        if (ttl != null && ttl == -1) {
            redisTemplate.expire(rateLimitKey, java.time.Duration.ofMinutes(1));
        }
        if (count != null && count > 5) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please wait a minute before requesting delivery pricing again.");
        }
        // 1. Fetch Restaurant Coordinates
        Map<String, Object> responseBody;
        try {
            responseBody = restaurantClient.getRestaurantById(id);
        } catch (Exception e) {
            throw new IllegalArgumentException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_NOT_FOUND + id);
        }
        if (responseBody == null || responseBody.get("data") == null) {
            throw new IllegalArgumentException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_NOT_FOUND + id);
        }
        Map<String, Object> restaurant = (Map<String, Object>) responseBody.get("data");
        Double rLat = (Double) restaurant.get("lat");
        Double rLng = (Double) restaurant.get("lng");
        if (rLat == null || rLng == null) {
            throw new IllegalStateException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_UNKNOWN);
        }
        String distanceCacheKey = "distance_cache:" + addressId + ":" + id;
        String cachedDistance = redisTemplate.opsForValue().get(distanceCacheKey);
        double distance = 5.0; // default fallback
        boolean isFallback = false;
        boolean cacheHit = false;
        if (cachedDistance != null) {
            try {
                distance = Double.parseDouble(cachedDistance);
                cacheHit = true;
            } catch (NumberFormatException e) {
                log.warn("Corrupted distance cache value for key {}: \'{}\'. Deleting and re-fetching.", distanceCacheKey, cachedDistance);
                redisTemplate.delete(distanceCacheKey);
                cachedDistance = null; // fall through to API call
            }
        }
        if (!cacheHit) {
            String origin = address.getLatitude() + "," + address.getLongitude();
            String destination = rLat + "," + rLng;
            Map<String, Object> distanceMap = mapsClient.getDistance(origin, destination);
            if (distanceMap != null) {
                if (distanceMap.containsKey("distance")) {
                    distance = ((Number) distanceMap.get("distance")).doubleValue();
                } else {
                    isFallback = true;
                }
                if (Boolean.TRUE.equals(distanceMap.get("fallback"))) {
                    isFallback = true;
                }
            } else {
                isFallback = true;
            }
            if (isFallback) {
                throw new IllegalArgumentException("Unable to calculate accurate delivery distance as the mapping service is currently unavailable. Please try again later.");
            }
            redisTemplate.opsForValue().set(distanceCacheKey, String.valueOf(distance), 1, java.util.concurrent.TimeUnit.HOURS);
        }
        java.math.BigDecimal minAmount = dynamicPricingService.getMinAmountForFreeDelivery(new java.math.BigDecimal(String.valueOf(distance)));
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("minimumOrderForFreeDelivery", minAmount);
        data.put("distanceKm", distance);
        data.put("fixedPlatformFee", dynamicPricingConfig.getFixedPlatformFee());
        return ResponseEntity.ok(ApiResponse.success(data, "Delivery pricing calculated"));
    }

    @java.lang.SuppressWarnings("all")
    public CustomerRestaurantController(final RestaurantClient restaurantClient, final MapsClient mapsClient, final com.fooddelivery.customer.service.DynamicPricingService dynamicPricingService, final com.fooddelivery.customer.config.DynamicPricingConfig dynamicPricingConfig, final com.fooddelivery.customer.repository.CustomerAddressRepository customerAddressRepository, final com.fooddelivery.customer.client.AdvertisementClient advertisementClient, final org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.restaurantClient = restaurantClient;
        this.mapsClient = mapsClient;
        this.dynamicPricingService = dynamicPricingService;
        this.dynamicPricingConfig = dynamicPricingConfig;
        this.customerAddressRepository = customerAddressRepository;
        this.advertisementClient = advertisementClient;
        this.redisTemplate = redisTemplate;
    }
}
