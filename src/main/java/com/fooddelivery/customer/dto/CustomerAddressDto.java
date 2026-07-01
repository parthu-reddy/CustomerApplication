package com.fooddelivery.customer.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CustomerAddressDto {
    private UUID id;
    private UUID customerId;
    private String label;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zipCode;
    private Double latitude;
    private Double longitude;
    private Boolean isDefault;
}
