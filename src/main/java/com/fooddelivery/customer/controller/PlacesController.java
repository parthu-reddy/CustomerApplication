package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.service.PlacesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/places")
@PreAuthorize("hasRole(\'CUSTOMER\')")
public class PlacesController {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlacesController.class);
    private final PlacesService placesService;

    @GetMapping("/autocomplete")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> autocomplete(@RequestParam String input) {
        return ResponseEntity.ok(ApiResponse.success(placesService.autocomplete(input), "Places retrieved"));
    }

    @GetMapping("/reverse-geocode")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reverseGeocode(@RequestParam double lat, @RequestParam double lng) {
        return ResponseEntity.ok(ApiResponse.success(placesService.reverseGeocode(lat, lng), "Address retrieved"));
    }

    @java.lang.SuppressWarnings("all")
    public PlacesController(final PlacesService placesService) {
        this.placesService = placesService;
    }
}
