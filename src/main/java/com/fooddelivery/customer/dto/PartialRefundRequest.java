package com.fooddelivery.customer.dto;

import java.math.BigDecimal;

@lombok.Data
public class PartialRefundRequest {
    private BigDecimal amount;
    private String reason;
    private com.fooddelivery.common.enums.FaultType faultType = com.fooddelivery.common.enums.FaultType.UNKNOWN;

    

    

    

    

    

    

    

    

    

    
}
