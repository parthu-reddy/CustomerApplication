package com.fooddelivery.customer.service;

import com.fooddelivery.customer.config.DynamicPricingConfig;
import com.fooddelivery.customer.model.PricingBreakdown;
import com.fooddelivery.order.entity.OrderCharge;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

public class DebugTest {
    @Test
    public void test() {
        DynamicPricingConfig config = new DynamicPricingConfig();
        config.setBasePrice(new BigDecimal("20.00"));
        config.setPerKmRate(new BigDecimal("10.00"));
        config.setRestMaxContributionPercent(new BigDecimal("0.20"));
        config.setFixedPlatformFee(new BigDecimal("5.00"));
        config.setPlatformExcessCutPercent(new BigDecimal("0.50"));
        config.setSgstPercent(new BigDecimal("0.025"));
        config.setCgstPercent(new BigDecimal("0.025"));
        config.setDeliverySgstPercent(new BigDecimal("0.05"));
        config.setDeliveryCgstPercent(new BigDecimal("0.05"));
        
        DynamicPricingService service = new DynamicPricingService(config);
        PricingBreakdown b = service.calculatePricing(new BigDecimal("3728.99"), new BigDecimal("5.00"));
        System.out.println("Charges size: " + b.getCharges().size());
        BigDecimal customerPays = BigDecimal.ZERO;
        for (OrderCharge c : b.getCharges()) {
            System.out.println("Charge: " + c.getCategory() + " Payer: " + c.getPayerType() + " Amount: " + c.getAmount());
            if (c.getPayerType() == com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER) {
                customerPays = customerPays.add(c.getAmount());
            }
        }
        System.out.println("Customer pays: " + customerPays);
    }
}
