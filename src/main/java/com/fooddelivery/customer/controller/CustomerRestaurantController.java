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
import com.fooddelivery.common.client.MapsServiceClient;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/restaurants")
@PreAuthorize("hasRole(\'CUSTOMER\')")
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class CustomerRestaurantController {
    

    private final RestaurantClient restaurantClient;
    private final MapsServiceClient mapsClient;
    private final com.fooddelivery.customer.service.DynamicPricingService dynamicPricingService;
    private final com.fooddelivery.customer.config.DynamicPricingConfig dynamicPricingConfig;
    private final com.fooddelivery.customer.repository.CustomerAddressRepository customerAddressRepository;
    private final com.fooddelivery.customer.client.AdvertisementClient advertisementClient;
    private final com.fooddelivery.customer.config.DeliveryZoneConfig deliveryZoneConfig;

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<com.fooddelivery.customer.dto.RestaurantDto>>> getNearbyRestaurants(@RequestParam double lat, @RequestParam double lng, @RequestParam(defaultValue = "5.0") double radius) {
        ApiResponse<List<com.fooddelivery.customer.dto.RestaurantDto>> response = restaurantClient.getNearbyRestaurants(lat, lng, radius);
        // Fetch ads from AdvertisementService (BiddingEngine)
        try {
            com.fooddelivery.customer.dto.AdRequestDTO adRequest = com.fooddelivery.customer.dto.AdRequestDTO.builder()
                    .geo("US")
                    .deviceId(java.util.UUID.randomUUID().toString())
                    .context("restaurant_listing")
                    .build();
            java.util.List<com.fooddelivery.customer.dto.SponsoredListingDTO> adResults = advertisementClient.fetchAds(adRequest);
            if (adResults != null && !adResults.isEmpty() && response.getData() != null) {
                com.fooddelivery.customer.dto.SponsoredListingDTO topAd = adResults.get(0);
                
                // Find matching restaurant by brandId = advertiserId
                com.fooddelivery.customer.dto.RestaurantDto matchedRestaurant = response.getData().stream()
                        .filter(r -> r.getBrandId() != null && r.getBrandId().toString().equals(topAd.getAdvertiserId()))
                        .findFirst()
                        .orElse(null);
                        
                com.fooddelivery.customer.dto.RestaurantDto sponsoredListing;
                if (matchedRestaurant != null) {
                    sponsoredListing = com.fooddelivery.customer.dto.RestaurantDto.builder()
                            .id(matchedRestaurant.getId())
                            .brandId(matchedRestaurant.getBrandId())
                            .name(matchedRestaurant.getName())
                            .description(matchedRestaurant.getDescription())
                            .lat(matchedRestaurant.getLat())
                            .lng(matchedRestaurant.getLng())
                            .address(matchedRestaurant.getAddress())
                            .rating(matchedRestaurant.getRating())
                            .isActive(matchedRestaurant.getIsActive())
                            .defaultPrepTimeSeconds(matchedRestaurant.getDefaultPrepTimeSeconds())
                            .isOpen(matchedRestaurant.getIsOpen())
                            .image(matchedRestaurant.getImage())
                            .logoUrl(matchedRestaurant.getLogoUrl())
                            .cuisine(matchedRestaurant.getCuisine())
                            .reviewsCount(matchedRestaurant.getReviewsCount())
                            .deliveryTime(matchedRestaurant.getDeliveryTime())
                            .deliveryFee(matchedRestaurant.getDeliveryFee())
                            .tags(matchedRestaurant.getTags())
                            .brandName(matchedRestaurant.getBrandName())
                            .distance(matchedRestaurant.getDistance())
                            .isSponsored(true)
                            .adData(topAd)
                            .build();
                } else {
                    sponsoredListing = com.fooddelivery.customer.dto.RestaurantDto.builder()
                            .isSponsored(true)
                            .adData(topAd)
                            .build();
                }
                
                // Add to the front of the list
                List<com.fooddelivery.customer.dto.RestaurantDto> merged = new java.util.ArrayList<>();
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
    public ResponseEntity<ApiResponse<List<com.fooddelivery.customer.dto.RestaurantDto>>> getBrandOutlets(@org.springframework.web.bind.annotation.PathVariable java.util.UUID brandId, @RequestParam double lat, @RequestParam double lng, @RequestParam(defaultValue = "5.0") double radius) {
        ApiResponse<List<com.fooddelivery.customer.dto.RestaurantDto>> response = restaurantClient.getBrandOutlets(brandId, lat, lng, radius);
        if (response.getData() != null) {
            String origin = lat + "," + lng;
            for (com.fooddelivery.customer.dto.RestaurantDto outlet : response.getData()) {
                Double oLat = outlet.getLat();
                Double oLng = outlet.getLng();
                if (oLat != null && oLng != null) {
                    try {
                        String destination = oLat.toString() + "," + oLng.toString();
                        com.fooddelivery.common.dto.maps.DistanceResponseDto distanceMap = mapsClient.getDistance(origin, destination);
                        if (distanceMap != null && distanceMap.getDistance() != null) {
                            outlet.setDistance(distanceMap.getDistance());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to get actual distance for outlet {}: {}", outlet.getId(), e.getMessage());
                    }
                }
            }
        }
        return ResponseEntity.ok(response);
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
        Double lat = com.fooddelivery.common.util.JsonNumberUtils.toDouble(restaurant.get("lat"));
        Double lng = com.fooddelivery.common.util.JsonNumberUtils.toDouble(restaurant.get("lng"));
        if (lat == null || lng == null) {
            throw new IllegalStateException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_UNKNOWN);
        }
        // 2. Check Driver Availability in MapsIntegration
        try {
            com.fooddelivery.common.dto.maps.FleetAvailabilityResponseDto mapsResponse = mapsClient.checkFleetAvailability(
                    deliveryZoneConfig.getDefaultCity(), lat, lng,
                    deliveryZoneConfig.getFleetSearchRadiusKm());
            if (mapsResponse != null) {
                Boolean available = mapsResponse.getAvailable();
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
    public ResponseEntity<ApiResponse<com.fooddelivery.customer.dto.DeliveryPricingDto>> getDeliveryPricing(@org.springframework.web.bind.annotation.PathVariable java.util.UUID id, @RequestParam java.util.UUID addressId, java.security.Principal principal) {
        com.fooddelivery.customer.entity.CustomerAddress address = customerAddressRepository.findById(addressId).orElseThrow(() -> new IllegalArgumentException("Address not found"));
        // SECURITY CHECK: Verify address belongs to authenticated customer
        if (principal == null || principal.getName() == null || !address.getCustomerId().toString().equals(principal.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        // RATE LIMITING: 5 requests per minute per user per restaurant using Redis Lua Script for atomic increment + expire
        String rateLimitKey = "rate_limit:delivery_pricing:" + principal.getName() + ":" + id;
        String luaScript = "local current = redis.call('incr', KEYS[1]) " +
                           "if tonumber(current) == 1 then " +
                           "  redis.call('expire', KEYS[1], ARGV[1]) " +
                           "end " +
                           "return tonumber(current)";
        org.springframework.data.redis.core.script.DefaultRedisScript<Long> redisScript = new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, Long.class);
        
        Long count = redisTemplate.execute(redisScript, java.util.Collections.singletonList(rateLimitKey), "60");
        
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
        Double rLat = com.fooddelivery.common.util.JsonNumberUtils.toDouble(restaurant.get("lat"));
        Double rLng = com.fooddelivery.common.util.JsonNumberUtils.toDouble(restaurant.get("lng"));
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
                log.warn("Corrupted distance cache value for key {}: '{}'. Deleting and re-fetching.", distanceCacheKey, cachedDistance);
                redisTemplate.delete(distanceCacheKey);
                cachedDistance = null; // fall through to API call
            }
        }
        if (!cacheHit) {
            String origin = address.getLatitude() + "," + address.getLongitude();
            String destination = rLat + "," + rLng;
            com.fooddelivery.common.dto.maps.DistanceResponseDto distanceMap = mapsClient.getDistance(origin, destination);
            if (distanceMap != null) {
                if (distanceMap.getDistance() != null) {
                    distance = distanceMap.getDistance().doubleValue();
                } else {
                    isFallback = true;
                }
                if (Boolean.TRUE.equals(distanceMap.getFallback())) {
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
        
        com.fooddelivery.customer.dto.PricingConfigDto configData = com.fooddelivery.customer.dto.PricingConfigDto.builder()
            .basePrice(dynamicPricingConfig.getBasePrice())
            .perKmRate(dynamicPricingConfig.getPerKmRate())
            .restMaxContributionPercent(dynamicPricingConfig.getRestMaxContributionPercent())
            .fixedPlatformFee(dynamicPricingConfig.getFixedPlatformFee())
            .platformExcessCutPercent(dynamicPricingConfig.getPlatformExcessCutPercent())
            .sgstPercent(dynamicPricingConfig.getSgstPercent())
            .cgstPercent(dynamicPricingConfig.getCgstPercent())
            .build();
            
        com.fooddelivery.customer.dto.DeliveryPricingDto data = com.fooddelivery.customer.dto.DeliveryPricingDto.builder()
            .distanceKm(distance)
            .config(configData)
            .build();
        
        return ResponseEntity.ok(ApiResponse.success(data, "Delivery pricing config retrieved"));
    }

    
}
