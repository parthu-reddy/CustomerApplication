package com.fooddelivery.customer.dto;

import java.math.BigDecimal;


@lombok.Data
public class DriverSummary {
    private int deliveries;
    private BigDecimal gross;
    private BigDecimal taxes;
    private BigDecimal net;
    /** Customers' tips on these deliveries; included in gross and net (tips are not taxed). */
    private BigDecimal tips;
    private BigDecimal pendingBalance;
    private PayoutSummaryDto lastPayout;
}
