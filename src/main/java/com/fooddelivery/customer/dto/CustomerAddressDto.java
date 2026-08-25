package com.fooddelivery.customer.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class CustomerAddressDto {
    @NotNull
    private UUID id;
    @NotNull
    private UUID customerId;
    @NotNull
    private String label;
    @NotNull
    private String addressLine1;
    private String addressLine2;
    @NotNull
    private String city;
    @NotNull
    private String state;
    @NotNull
    private String zipCode;
    @NotNull
    private Double latitude;
    @NotNull
    private Double longitude;
    private Boolean isDefault;

}
