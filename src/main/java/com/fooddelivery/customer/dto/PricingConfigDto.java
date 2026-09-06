package com.fooddelivery.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingConfigDto {
    private BigDecimal basePrice;
    private BigDecimal perKmRate;
    private BigDecimal restMaxContributionPercent;
    private BigDecimal fixedPlatformFee;
    private BigDecimal platformExcessCutPercent;
    private BigDecimal sgstPercent;
    private BigDecimal cgstPercent;
}
