package com.fooddelivery.customer.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class CustomerProfileRequest {
    private String name;

    @Email(message = "Invalid email format")
    private String email;
}
