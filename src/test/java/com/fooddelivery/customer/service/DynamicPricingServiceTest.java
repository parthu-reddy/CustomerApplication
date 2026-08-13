package com.fooddelivery.customer.service;

import com.fooddelivery.customer.config.DynamicPricingConfig;
import com.fooddelivery.customer.model.PricingBreakdown;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fooddelivery.order.entity.OrderCharge;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.order.enums.ChargeEntityType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicPricingServiceTest {

    private DynamicPricingService pricingService;

    @BeforeEach
    void setUp() {
        DynamicPricingConfig config = new DynamicPricingConfig();
        // Base Price = ₹15, Per KM = ₹8, Max Rest = 15%, Fixed Platform = ₹5, Excess Cut = 50%
        config.setBasePrice(new BigDecimal("15.00"));
        config.setPerKmRate(new BigDecimal("8.00"));
        config.setRestMaxContributionPercent(new BigDecimal("0.15"));
        config.setFixedPlatformFee(new BigDecimal("5.00"));
        config.setPlatformExcessCutPercent(new BigDecimal("0.50"));
        config.setSgstPercent(new BigDecimal("0.025"));
        config.setCgstPercent(new BigDecimal("0.025"));
        config.setDeliverySgstPercent(new BigDecimal("0.09"));
        config.setDeliveryCgstPercent(new BigDecimal("0.09"));
        
        pricingService = new DynamicPricingService(config);
    }

    @Test
    void testSmallFarOrder() {
        // Food Cost = 200, Dist = 5km
        PricingBreakdown result = pricingService.calculatePricing(new BigDecimal("200.00"), new BigDecimal("5.00"));
        
        assertEquals(new BigDecimal("30.00"), result.getTotalCustomerDeliveryFee());
        
        Set<OrderCharge> charges = result.getCharges();
        assertChargeExists(charges, ChargeCategory.FOOD_COST, ChargeEntityType.PLATFORM, ChargeEntityType.RESTAURANT, "200.00");
        assertChargeExists(charges, ChargeCategory.PLATFORM_FIXED_FEE, ChargeEntityType.CUSTOMER, ChargeEntityType.PLATFORM, "5.00");
        assertChargeExists(charges, ChargeCategory.DELIVERY_FEE, ChargeEntityType.CUSTOMER, ChargeEntityType.DRIVER, "25.00");
        assertChargeExists(charges, ChargeCategory.PLATFORM_FIXED_FEE, ChargeEntityType.RESTAURANT, ChargeEntityType.PLATFORM, "5.00");
        assertChargeExists(charges, ChargeCategory.DELIVERY_FEE, ChargeEntityType.RESTAURANT, ChargeEntityType.DRIVER, "30.00");
        assertChargeExists(charges, ChargeCategory.SGST, ChargeEntityType.CUSTOMER, ChargeEntityType.GOVERNMENT, "5.00");
        assertChargeExists(charges, ChargeCategory.CGST, ChargeEntityType.CUSTOMER, ChargeEntityType.GOVERNMENT, "5.00");
        assertChargeExists(charges, ChargeCategory.SGST, ChargeEntityType.DRIVER, ChargeEntityType.GOVERNMENT, "4.95");
        assertChargeExists(charges, ChargeCategory.CGST, ChargeEntityType.DRIVER, ChargeEntityType.GOVERNMENT, "4.95");
    }

    @Test
    void testMedMedOrder() {
        // Food Cost = 500, Dist = 3km
        PricingBreakdown result = pricingService.calculatePricing(new BigDecimal("500.00"), new BigDecimal("3.00"));
        
        assertEquals(new BigDecimal("5.00"), result.getTotalCustomerDeliveryFee());
        
        Set<OrderCharge> charges = result.getCharges();
        assertChargeExists(charges, ChargeCategory.FOOD_COST, ChargeEntityType.PLATFORM, ChargeEntityType.RESTAURANT, "500.00");
        assertChargeExists(charges, ChargeCategory.PLATFORM_FIXED_FEE, ChargeEntityType.CUSTOMER, ChargeEntityType.PLATFORM, "5.00");
        assertChargeExists(charges, ChargeCategory.PLATFORM_FIXED_FEE, ChargeEntityType.RESTAURANT, ChargeEntityType.PLATFORM, "5.00");
        assertChargeExists(charges, ChargeCategory.DELIVERY_FEE, ChargeEntityType.RESTAURANT, ChargeEntityType.DRIVER, "39.00");
        assertChargeExists(charges, ChargeCategory.PLATFORM_BONUS, ChargeEntityType.RESTAURANT, ChargeEntityType.PLATFORM, "18.00");
        assertChargeExists(charges, ChargeCategory.SGST, ChargeEntityType.CUSTOMER, ChargeEntityType.GOVERNMENT, "12.50");
        assertChargeExists(charges, ChargeCategory.CGST, ChargeEntityType.CUSTOMER, ChargeEntityType.GOVERNMENT, "12.50");
        assertChargeExists(charges, ChargeCategory.SGST, ChargeEntityType.DRIVER, ChargeEntityType.GOVERNMENT, "3.51");
        assertChargeExists(charges, ChargeCategory.CGST, ChargeEntityType.DRIVER, ChargeEntityType.GOVERNMENT, "3.51");
    }

    @Test
    void testLargeNearOrder() {
        // Food Cost = 1000, Dist = 2km
        PricingBreakdown result = pricingService.calculatePricing(new BigDecimal("1000.00"), new BigDecimal("2.00"));
        
        assertEquals(new BigDecimal("5.00"), result.getTotalCustomerDeliveryFee());
        
        Set<OrderCharge> charges = result.getCharges();
        assertChargeExists(charges, ChargeCategory.FOOD_COST, ChargeEntityType.PLATFORM, ChargeEntityType.RESTAURANT, "1000.00");
        assertChargeExists(charges, ChargeCategory.PLATFORM_FIXED_FEE, ChargeEntityType.CUSTOMER, ChargeEntityType.PLATFORM, "5.00");
        assertChargeExists(charges, ChargeCategory.PLATFORM_FIXED_FEE, ChargeEntityType.RESTAURANT, ChargeEntityType.PLATFORM, "5.00");
        assertChargeExists(charges, ChargeCategory.DELIVERY_FEE, ChargeEntityType.RESTAURANT, ChargeEntityType.DRIVER, "31.00");
        assertChargeExists(charges, ChargeCategory.PLATFORM_BONUS, ChargeEntityType.RESTAURANT, ChargeEntityType.PLATFORM, "59.50");
        assertChargeExists(charges, ChargeCategory.SGST, ChargeEntityType.CUSTOMER, ChargeEntityType.GOVERNMENT, "25.00");
        assertChargeExists(charges, ChargeCategory.CGST, ChargeEntityType.CUSTOMER, ChargeEntityType.GOVERNMENT, "25.00");
        assertChargeExists(charges, ChargeCategory.SGST, ChargeEntityType.DRIVER, ChargeEntityType.GOVERNMENT, "2.79");
        assertChargeExists(charges, ChargeCategory.CGST, ChargeEntityType.DRIVER, ChargeEntityType.GOVERNMENT, "2.79");
    }
    
    private void assertChargeExists(Set<OrderCharge> charges, ChargeCategory category, ChargeEntityType payer, ChargeEntityType payee, String amount) {
        boolean exists = charges.stream().anyMatch(c -> 
            c.getCategory() == category &&
            c.getPayerType() == payer &&
            c.getPayeeType() == payee &&
            c.getAmount().compareTo(new BigDecimal(amount)) == 0
        );
        assertTrue(exists, "Expected charge not found: " + category + " " + payer + "->" + payee + " " + amount);
    }
}
