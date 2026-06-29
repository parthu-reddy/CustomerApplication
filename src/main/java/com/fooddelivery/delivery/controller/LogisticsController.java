package com.fooddelivery.delivery.controller;

import com.fooddelivery.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    @GetMapping("/route")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRoute(
            @RequestParam double sourceLat, @RequestParam double sourceLng,
            @RequestParam double destLat, @RequestParam double destLng) {
        
        // Mocking Ola Maps Routing API for the monolith
        Map<String, Object> routeData = Map.of(
            "distanceMeters", 4500,
            "durationSeconds", 900,
            "polyline", "mock_polyline_xyz"
        );
        
        return ResponseEntity.ok(ApiResponse.success(routeData, "Route calculated"));
    }

}
