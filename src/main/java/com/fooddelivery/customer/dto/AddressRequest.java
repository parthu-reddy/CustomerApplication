package com.fooddelivery.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@lombok.Data
public class AddressRequest {
    @NotBlank
    private String label;
    @NotBlank
    private String addressLine1;
    private String addressLine2;
    @NotBlank
    private String city;
    /** Optional only while one fleet city is configured; then the server supplies that scope. */
    @jakarta.validation.constraints.Size(max = 64)
    @com.fooddelivery.common.location.CityId
    @jakarta.validation.constraints.Pattern(regexp = com.fooddelivery.common.location.CityIdValidator.REGEX)
    private String cityId;
    @NotBlank
    private String state;
    @NotBlank
    private String zipCode;
    @NotNull
    private Double latitude;
    @NotNull
    private Double longitude;
}
