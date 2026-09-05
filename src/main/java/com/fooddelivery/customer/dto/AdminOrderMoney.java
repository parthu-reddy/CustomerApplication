package com.fooddelivery.customer.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;
import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;

@Data
public class AdminOrderMoney {
    private UUID orderId;
    private BigDecimal totalAmount;
    private BigDecimal foodCost;
    private BigDecimal deliveryFee;
    private BigDecimal customerPlatformFee;
    
    private BigDecimal restaurantPayout;
    private BigDecimal restaurantPlatformFee;
    private BigDecimal restaurantDeliveryContribution;

    private BigDecimal driverGrossPayout;
    private BigDecimal driverTaxes;
    private BigDecimal driverNetPayout;
    private BigDecimal platformBonus;

    private BigDecimal sgst;
    private BigDecimal cgst;

    private java.util.List<LedgerStatementLineDto> ledgerLines;
}
