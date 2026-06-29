package com.fooddelivery.customer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacesService {

    private final RestTemplate restTemplate;

    public List<Map<String, Object>> autocomplete(String input) {
        log.info("Requesting autocomplete for input: {} from MapsIntegration", input);
        try {
            String mapsServiceUrl = "http://localhost:8083/api/places/autocomplete?input=" + input;
            ResponseEntity<List> response = restTemplate.getForEntity(mapsServiceUrl, List.class);
            return response.getStatusCode().is2xxSuccessful() ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch autocomplete results", e);
            return Collections.emptyList();
        }
    }

    public Map<String, Object> reverseGeocode(double lat, double lng) {
        log.info("Requesting reverse geocode for lat: {}, lng: {} from MapsIntegration", lat, lng);
        try {
            String mapsServiceUrl = String.format("http://localhost:8083/api/places/reverse-geocode?lat=%f&lng=%f", lat, lng);
            ResponseEntity<Map> response = restTemplate.getForEntity(mapsServiceUrl, Map.class);
            return response.getStatusCode().is2xxSuccessful() ? response.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            log.error("Failed to fetch reverse geocode results", e);
            return Collections.emptyMap();
        }
    }
}
