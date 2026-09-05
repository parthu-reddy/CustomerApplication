package com.fooddelivery.customer.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.JsonNode;

@lombok.Data
public class DriverSummary {
    private int deliveries;
    private BigDecimal gross;
    private BigDecimal taxes;
    private BigDecimal net;
    private BigDecimal cashCollected;
    private BigDecimal cashRemitted;
    private BigDecimal cashInHand;
    private BigDecimal pendingBalance;
    private JsonNode lastPayout;
}
