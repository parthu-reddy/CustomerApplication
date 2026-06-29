package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.restaurant.entity.Restaurant;
import com.fooddelivery.restaurant.repository.IRestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class CustomerRestaurantController {

    private final IRestaurantRepository restaurantRepository;

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<Restaurant>>> getNearbyRestaurants(
            @RequestParam double lat, 
            @RequestParam double lng,
            @RequestParam(defaultValue = "5000") double radius) {
        
        List<Restaurant> restaurants = restaurantRepository.findNearbyActiveRestaurants(lat, lng, radius);
        return ResponseEntity.ok(ApiResponse.success(restaurants, "Nearby restaurants retrieved"));
    }
}
