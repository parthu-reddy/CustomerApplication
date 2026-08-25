package com.fooddelivery.customer.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OrderItemResponse {
    @NotNull
    private UUID id;
    @NotNull
    private UUID menuItemId;
    @NotNull
    private String name;
    @NotNull
    private Integer quantity;
    @NotNull
    private BigDecimal price;


    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    
}
