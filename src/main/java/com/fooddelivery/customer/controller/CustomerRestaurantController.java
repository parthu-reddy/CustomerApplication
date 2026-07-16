package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerRestaurantController {

    private final RestTemplate restTemplate;

    private static final String RESTAURANT_SERVICE_URL = "http://restaurant-service";

    private static final String MAPS_SERVICE_URL = "http://mapsintegration";

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<Object>>> getNearbyRestaurants(
            @RequestParam double lat, 
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radius) {
        
        ResponseEntity<ApiResponse<List<Object>>> response = restTemplate.exchange(
            RESTAURANT_SERVICE_URL + "/api/v1/restaurants/nearby?lat=" + lat + "&lng=" + lng + "&radius=" + radius,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<ApiResponse<List<Object>>>() {}
        );
        
        return ResponseEntity.ok(response.getBody());
    }

    @GetMapping("/{id}/delivery-availability")
    public ResponseEntity<ApiResponse<Boolean>> checkDeliveryAvailability(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        
        // 1. Fetch Restaurant Coordinates
        ResponseEntity<java.util.Map> restaurantResponse = restTemplate.getForEntity(
            RESTAURANT_SERVICE_URL + "/api/v1/restaurants/" + id, java.util.Map.class);
            
        if (!restaurantResponse.getStatusCode().is2xxSuccessful() || restaurantResponse.getBody() == null) {
            throw new IllegalArgumentException(com.fooddelivery.common.constants.AppConstants.ERROR_MSG_RESTAURANT_NOT_FOUND + id);
        }
        
        java.util.Map<String, Object> responseBody = restaurantResponse.getBody();
        java.util.Map<String, Object> restaurant = (java.util.Map<String, Object>) responseBody.get("data");
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
            ResponseEntity<java.util.Map> mapsResponse = restTemplate.getForEntity(
                MAPS_SERVICE_URL + "/api/fleet/availability/check?cityId=" + com.fooddelivery.common.constants.AppConstants.DEFAULT_CITY_ID + "&lat=" + lat + "&lng=" + lng + "&radius=" + com.fooddelivery.common.constants.AppConstants.MAX_DELIVERY_RADIUS_KM, java.util.Map.class);
                
            if (mapsResponse.getStatusCode().is2xxSuccessful() && mapsResponse.getBody() != null) {
                Boolean available = (Boolean) mapsResponse.getBody().get("available");
                if (Boolean.TRUE.equals(available)) {
                    return ResponseEntity.ok(ApiResponse.success(true, "Delivery partner available."));
                }
            }
        } catch (org.springframework.web.client.RestClientException e) {
            // Log and allow it to fall through to the unavailable exception
            System.err.println("Failed to reach MapsIntegration for fleet check: " + e.getMessage());
        }
        
        // 3. Throw Exception if not available
        throw new com.fooddelivery.customer.exception.DeliveryPartnerUnavailableException(
            com.fooddelivery.common.constants.AppConstants.ERROR_MSG_NO_DELIVERY_PARTNER_NEARBY, 
            com.fooddelivery.common.constants.AppConstants.ERROR_NO_DELIVERY_PARTNER_NEARBY
        );
    }
}
