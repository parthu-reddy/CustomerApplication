package com.fooddelivery.restaurant.service;

import com.fooddelivery.restaurant.entity.Restaurant;
import com.fooddelivery.restaurant.repository.IRestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class RestaurantOnboardingService {

    private final IRestaurantRepository restaurantRepository;
    private final RestTemplate restTemplate;

    @Value("${kyb.fssai.api.url:http://localhost:8080/mock/fssai}")
    private String fssaiApiUrl;

    @Value("${kyb.gstin.api.url:http://localhost:8080/mock/gstin}")
    private String gstinApiUrl;

    @Value("${kyb.pan.api.url:http://localhost:8080/mock/pan}")
    private String panApiUrl;

    @Value("${kyb.cin.api.url:http://localhost:8080/mock/cin}")
    private String cinApiUrl;

    @Value("${kyb.pennydrop.api.url:http://localhost:8080/mock/pennydrop}")
    private String pennyDropApiUrl;

    public Restaurant startOnboarding(String name, String fssai, String gstin, String pan, String cin, String bankAccountNumber, String ifscCode, Double lat, Double lng) {
        log.info("Starting onboarding for restaurant: {}, FSSAI: {}, GSTIN: {}, PAN: {}, CIN: {}", name, fssai, gstin, pan, cin);
        
        // External KYB Verifications in parallel
        CompletableFuture<Boolean> fssaiFuture = CompletableFuture.supplyAsync(() -> verifyFssai(fssai));
        CompletableFuture<Boolean> gstinFuture = CompletableFuture.supplyAsync(() -> verifyGstin(gstin));
        CompletableFuture<Boolean> panFuture = CompletableFuture.supplyAsync(() -> verifyPan(pan));
        CompletableFuture<Boolean> cinFuture = CompletableFuture.supplyAsync(() -> verifyCin(cin));
        CompletableFuture<Boolean> bankAccountFuture = CompletableFuture.supplyAsync(() -> verifyBankAccount(bankAccountNumber, ifscCode));

        CompletableFuture.allOf(fssaiFuture, gstinFuture, panFuture, cinFuture, bankAccountFuture).join();

        boolean isFssaiValid = fssaiFuture.join();
        boolean isGstinValid = gstinFuture.join();
        boolean isPanValid = panFuture.join();
        boolean isCinValid = cinFuture.join();
        boolean isBankAccountValid = bankAccountFuture.join();
        
        if (!isFssaiValid || !isGstinValid || !isPanValid || !isCinValid || !isBankAccountValid) {
            throw new IllegalArgumentException("Invalid KYC/KYB documents or Bank details based on government/bank records.");
        }
        
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        Point locationPoint = null;
        if (lat != null && lng != null) {
            locationPoint = geometryFactory.createPoint(new Coordinate(lng, lat)); // Note: longitude is X, latitude is Y
        }
        
        Restaurant restaurant = Restaurant.builder()
                .id(UUID.randomUUID())
                .name(name)
                .fssaiLicenseNumber(fssai)
                .gstin(gstin)
                .pan(pan)
                .cin(cin)
                .isActive(true) // Automatically active after mock validation
                .location(locationPoint)
                .createdAt(LocalDateTime.now())
                .build();
                
        return restaurantRepository.save(restaurant);
    }
    
    private boolean verifyFssai(String fssai) {
        log.info("Verifying FSSAI against external API for {}", fssai);
        if (fssai == null || fssai.length() < 10) return false;
        
        try {
            // Mocking the call: In production, this would hit Karza or Signzy
            ResponseEntity<Map> response = restTemplate.getForEntity(fssaiApiUrl + "?fssai=" + fssai, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("FSSAI API call failed, falling back to mock logic for demonstration", e);
            return true;
        }
    }
    
    private boolean verifyGstin(String gstin) {
        log.info("Verifying GSTIN against external API for {}", gstin);
        if (gstin == null || gstin.length() != 15) return false;
        
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(gstinApiUrl + "?gstin=" + gstin, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("GSTIN API call failed, falling back to mock logic for demonstration", e);
            return true;
        }
    }

    private boolean verifyPan(String pan) {
        log.info("Verifying PAN against external API for {}", pan);
        if (pan == null || pan.length() != 10) return false;
        
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(panApiUrl + "?pan=" + pan, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("PAN API call failed, falling back to mock logic for demonstration", e);
            return true;
        }
    }

    private boolean verifyCin(String cin) {
        log.info("Verifying CIN against external API for {}", cin);
        if (cin == null || cin.length() != 21) return false;
        
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(cinApiUrl + "?cin=" + cin, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("CIN API call failed, falling back to mock logic for demonstration", e);
            return true;
        }
    }

    private boolean verifyBankAccount(String bankAccountNumber, String ifscCode) {
        log.info("Verifying Bank Account using Penny Drop API for A/C: {}, IFSC: {}", bankAccountNumber, ifscCode);
        if (bankAccountNumber == null || ifscCode == null || bankAccountNumber.length() < 9) return false;
        
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(pennyDropApiUrl + "?account=" + bankAccountNumber + "&ifsc=" + ifscCode, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Penny Drop API call failed, falling back to mock logic for demonstration", e);
            return true;
        }
    }
}
