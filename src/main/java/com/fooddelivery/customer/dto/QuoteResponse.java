package com.fooddelivery.customer.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponse {
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal platformFee;
    private BigDecimal sgst;
    private BigDecimal cgst;
    private BigDecimal total;
    private BigDecimal minAmountForFreeDelivery;
    private BigDecimal distanceKm;
    private BigDecimal driverPayout;
    private BigDecimal restaurantDeliveryContribution;
}
