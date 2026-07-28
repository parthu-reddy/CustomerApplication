package com.fooddelivery.customer.model;

import com.fooddelivery.order.entity.OrderCharge;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PricingBreakdown {
    private BigDecimal totalCustomerDeliveryFee;
    private BigDecimal sgst;
    private BigDecimal cgst;
    private List<OrderCharge> charges;
}
