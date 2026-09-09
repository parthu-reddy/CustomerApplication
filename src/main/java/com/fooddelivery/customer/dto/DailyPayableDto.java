package com.fooddelivery.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * What the order book says was owed to restaurants and riders for one day's deliveries.
 *
 * <p>The counterpart the ledger's PAYABLE_VS_ORDERS reconciliation compares itself against. Until
 * 2026-09-09 there was no such endpoint and {@code checkPayableVsOrders} was a stub that logged a
 * warning -- one of the six nightly checks had never run, while the gate reported all six covered
 * because the kind names appeared in a comment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPayableDto {
    private BigDecimal restaurantPayable;
    private BigDecimal driverPayable;
}
