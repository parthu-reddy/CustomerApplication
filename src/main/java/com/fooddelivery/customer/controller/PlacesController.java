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
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class PlacesController {
    

    private final PlacesService placesService;

    @GetMapping("/autocomplete")
    public ResponseEntity<ApiResponse<List<com.fooddelivery.common.dto.maps.PlaceAutocompleteDto>>> autocomplete(@RequestParam String input) {
        List<com.fooddelivery.common.dto.maps.PlaceAutocompleteDto> results = placesService.autocomplete(input);
        return ResponseEntity.ok(ApiResponse.success(results, "Success"));
    }

    @GetMapping("/reverse-geocode")
    public ResponseEntity<ApiResponse<com.fooddelivery.common.dto.maps.PlaceGeocodeDto>> reverseGeocode(@RequestParam double lat, @RequestParam double lng) {
        com.fooddelivery.common.dto.maps.PlaceGeocodeDto result = placesService.reverseGeocode(lat, lng);
        return ResponseEntity.ok(ApiResponse.success(result, "Success"));
    }

    
}
