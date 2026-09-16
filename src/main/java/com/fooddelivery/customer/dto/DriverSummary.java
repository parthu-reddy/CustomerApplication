package com.fooddelivery.customer.dto;

import java.math.BigDecimal;


@lombok.Data
public class DriverSummary {
    private int deliveries;
    private BigDecimal gross;
    private BigDecimal taxes;
    private BigDecimal net;
    private BigDecimal pendingBalance;
    private PayoutSummaryDto lastPayout;
}
