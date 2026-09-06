package com.fooddelivery.customer.dto;

import java.math.BigDecimal;


@lombok.Data
public class RestaurantSummary {
    private int orders;
    private BigDecimal grossFoodCost;
    private BigDecimal platformFees;
    private BigDecimal deliveryContribution;
    private BigDecimal platformBonus;
    private BigDecimal netEarnings;
    private BigDecimal clawbacks;
    private BigDecimal pendingBalance;
    private PayoutSummaryDto lastPayout;
    private BeneficiaryStatusDto beneficiaryStatus;
}
