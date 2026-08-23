package com.fooddelivery.customer.dto;

import java.util.UUID;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OrderItemRequest {
    @NotNull
    private UUID menuItemId;
    @NotNull
    @Min(1)
    private Integer quantity;


    

    

    

    

    

    

    

    

    

    

    
}
