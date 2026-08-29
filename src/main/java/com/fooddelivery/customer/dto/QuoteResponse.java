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
    /** Redeem this at checkout; the order is charged exactly what this quote says. */
    @NotNull
    private java.util.UUID quoteId;
    /** After this instant the quote is dead and the customer must re-quote. */
    @NotNull
    private java.time.LocalDateTime expiresAt;
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
