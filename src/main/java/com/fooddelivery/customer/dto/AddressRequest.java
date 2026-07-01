package com.fooddelivery.customer.dto;

import lombok.Data;

@Data
public class AddressRequest {
    private String label;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zipCode;
    private Double latitude;
    private Double longitude;
}
