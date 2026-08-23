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
    @NotBlank
    private String state;
    @NotBlank
    private String zipCode;
    @NotNull
    private Double latitude;
    @NotNull
    private Double longitude;

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    
}
