package com.fooddelivery.customer.client;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MapsClientFallback implements MapsClient {

    @Override
    public Map<String, Object> checkFleetAvailability(String cityId, double lat, double lng, double radius) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("available", true);
        fallback.put("count", 3);
        fallback.put("fallback", true);
        return fallback;
    }

    @Override
    public List<Map<String, Object>> autocomplete(String input) {
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> reverseGeocode(double lat, double lng) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("formatted_address", "Current Location (Offline Geocoder)");
        fallback.put("lat", lat);
        fallback.put("lng", lng);
        fallback.put("fallback", true);
        return fallback;
    }

    @Override
    public Map<String, Object> getDistance(String origin, String destination) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("distance", 5.0); // 5km fallback
        fallback.put("fallback", true);
        return fallback;
    }
}
