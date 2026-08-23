package com.fooddelivery.customer.model;

import com.fooddelivery.order.entity.OrderCharge;
import java.math.BigDecimal;
import java.util.Set;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class PricingBreakdown {
    private BigDecimal totalCustomerDeliveryFee;
    private BigDecimal sgst;
    private BigDecimal cgst;

    private BigDecimal itemTotal;
    private BigDecimal customerPlatformFee;
    private BigDecimal restaurantPlatformFee;
    private BigDecimal platformBonus;
    private BigDecimal restaurantDeliveryContribution;
    private BigDecimal restaurantPayout;
    private BigDecimal deliveryFee;
    private BigDecimal driverGrossPayout;
    private BigDecimal driverTaxes;
    private BigDecimal driverNetPayout;

    private Set<OrderCharge> charges;

}
