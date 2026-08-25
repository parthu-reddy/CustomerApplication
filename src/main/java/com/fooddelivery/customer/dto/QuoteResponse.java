package com.fooddelivery.customer.dto;

import jakarta.validation.constraints.NotNull;
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
    @NotNull
    private BigDecimal subtotal;
    @NotNull
    private BigDecimal deliveryFee;
    @NotNull
    private BigDecimal platformFee;
    @NotNull
    private BigDecimal sgst;
    @NotNull
    private BigDecimal cgst;
    @NotNull
    private BigDecimal total;
    @NotNull
    private BigDecimal minAmountForFreeDelivery;
    @NotNull
    private BigDecimal distanceKm;
    @NotNull
    private BigDecimal driverPayout;
    @NotNull
    private BigDecimal restaurantDeliveryContribution;
}
