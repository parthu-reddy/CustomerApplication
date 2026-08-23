package com.fooddelivery.customer.dto;

import java.math.BigDecimal;
import java.util.UUID;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OrderItemResponse {
    private UUID id;
    private UUID menuItemId;
    private String name;
    private Integer quantity;
    private BigDecimal price;


    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    
}
