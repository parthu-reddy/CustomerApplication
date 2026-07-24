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
import com.fooddelivery.customer.client.RestaurantClient;
import com.fooddelivery.customer.client.MapsClient;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerRestaurantController {

    private final RestaurantClient restaurantClient;
    private final MapsClient mapsClient;

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<Object>>> getNearbyRestaurants(
            @RequestParam double lat, 
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radius) {
        
        ApiResponse<List<Object>> response = restaurantClient.getNearbyRestaurants(lat, lng, radius);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/brands/{brandId}/outlets")
    public ResponseEntity<ApiResponse<List<Object>>> getBrandOutlets(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID brandId,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radius) {
        return ResponseEntity.ok(restaurantClient.getBrandOutlets(brandId, lat, lng, radius));
    }

    @GetMapping("/{id}/delivery-availability")
    public ResponseEntity<ApiResponse<Boolean>> checkDeliveryAvailability(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        
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
            Map<String, Object> mapsResponse = mapsClient.checkFleetAvailability(
                com.fooddelivery.common.constants.AppConstants.DEFAULT_CITY_ID, lat, lng, 
                com.fooddelivery.common.constants.AppConstants.MAX_DELIVERY_RADIUS_KM);
                
            if (mapsResponse != null) {
                Boolean available = (Boolean) mapsResponse.get("available");
                if (Boolean.TRUE.equals(available)) {
                    return ResponseEntity.ok(ApiResponse.success(true, "Delivery partner available."));
                }
            }
        } catch (Exception e) {
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
