package com.fooddelivery.customer.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteRequest {
    @NotNull
    private UUID restaurantId;
    @NotNull
    private UUID deliveryAddressId;
    @Valid
    private List<OrderItemRequest> items;
}
