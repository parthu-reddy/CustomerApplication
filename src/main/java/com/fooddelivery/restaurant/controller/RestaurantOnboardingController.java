package com.fooddelivery.restaurant.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.restaurant.entity.Restaurant;
import com.fooddelivery.restaurant.service.RestaurantOnboardingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class RestaurantOnboardingController {

    private final RestaurantOnboardingService onboardingService;

    @PostMapping("/onboard")
    public ResponseEntity<ApiResponse<Restaurant>> onboardRestaurant(@RequestBody OnboardRequest request) {
        Restaurant restaurant = onboardingService.startOnboarding(
                request.getName(),
                request.getFssaiLicenseNumber(),
                request.getGstin(),
                request.getPan(),
                request.getCin(),
                request.getBankAccountNumber(),
                request.getIfscCode(),
                request.getLat(),
                request.getLng()
        );
        return ResponseEntity.ok(ApiResponse.success(restaurant, "Restaurant onboarded successfully"));
    }

    @Data
    public static class OnboardRequest {
        private String name;
        private String fssaiLicenseNumber;
        private String gstin;
        private String pan;
        private String cin;
        private String bankAccountNumber;
        private String ifscCode;
        private Double lat;
        private Double lng;
    }
}
