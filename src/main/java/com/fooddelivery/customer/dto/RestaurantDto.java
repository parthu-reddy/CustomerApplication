package com.fooddelivery.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RestaurantDto {
    private UUID id;
    private UUID brandId;
    private String name;
    private String description;
    private Double lat;
    private Double lng;
    private String address;
    private Double rating;
    
    // Derived/Dynamic fields added by CustomerApplication
    private Double distance;
    private Boolean isSponsored;
    private Object adData;
    
    // For any unmapped dynamic fields from the downstream RestaurantService
    private Map<String, Object> additionalAttributes;
}
