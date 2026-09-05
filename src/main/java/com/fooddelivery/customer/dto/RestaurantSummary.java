package com.fooddelivery.customer.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.JsonNode;

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
    private JsonNode lastPayout;
    private JsonNode beneficiaryStatus;
}
