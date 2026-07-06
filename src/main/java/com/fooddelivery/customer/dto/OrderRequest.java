package com.fooddelivery.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    @NotNull
    private UUID customerId;
    @NotNull
    private UUID restaurantId;
    @NotNull
    private UUID deliveryAddressId;
    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;
}
