package com.fooddelivery.customer.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PartialRefundRequest {
    private BigDecimal amount;
    private String reason;
}
