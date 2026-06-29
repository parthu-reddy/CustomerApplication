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

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class CustomerRestaurantController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String RESTAURANT_SERVICE_URL = "http://localhost:8094";

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<Object>>> getNearbyRestaurants(
            @RequestParam double lat, 
            @RequestParam double lng,
            @RequestParam(defaultValue = "5000") double radius) {
        
        ResponseEntity<ApiResponse<List<Object>>> response = restTemplate.exchange(
            RESTAURANT_SERVICE_URL + "/api/v1/restaurants/nearby?lat=" + lat + "&lng=" + lng + "&radius=" + radius,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<ApiResponse<List<Object>>>() {}
        );
        
        return ResponseEntity.ok(response.getBody());
    }
}
