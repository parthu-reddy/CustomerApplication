package com.fooddelivery.customer.service;

import org.springframework.stereotype.Service;
import com.fooddelivery.common.client.MapsServiceClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class PlacesService {
    

    private final MapsServiceClient mapsClient;

    public List<com.fooddelivery.common.dto.maps.PlaceAutocompleteDto> autocomplete(String input) {
        log.info("Requesting autocomplete for input: {} from MapsIntegration", input);
        try {
            return mapsClient.autocomplete(input);
        } catch (Exception e) {
            log.error("Failed to fetch autocomplete results", e);
            throw new IllegalStateException("Maps integration failed to provide autocomplete results", e);
        }
    }

    public com.fooddelivery.common.dto.maps.PlaceGeocodeDto reverseGeocode(double lat, double lng) {
        log.info("Requesting reverse geocode for lat: {}, lng: {} from MapsIntegration", lat, lng);
        try {
            return mapsClient.reverseGeocode(lat, lng);
        } catch (Exception e) {
            log.error("Failed to fetch reverse geocode results", e);
            throw new IllegalStateException("Maps integration failed to provide reverse geocode results", e);
        }
    }

    
}
