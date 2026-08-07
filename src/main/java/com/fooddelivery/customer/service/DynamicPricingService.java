package com.fooddelivery.customer.service;

import com.fooddelivery.customer.config.DynamicPricingConfig;
import com.fooddelivery.customer.model.PricingBreakdown;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DynamicPricingService {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DynamicPricingService.class);
    private final DynamicPricingConfig config;

    public PricingBreakdown calculatePricing(BigDecimal foodCost, BigDecimal distanceKm) {
        BigDecimal effectiveDistance = distanceKm.max(BigDecimal.ONE);
        BigDecimal driverPayout = config.getBasePrice().add(effectiveDistance.multiply(config.getPerKmRate()));
        BigDecimal maxRestContribution = foodCost.multiply(config.getRestMaxContributionPercent());
        BigDecimal excessBudget = maxRestContribution.subtract(driverPayout).max(BigDecimal.ZERO);
        BigDecimal platformBonus = excessBudget.multiply(config.getPlatformExcessCutPercent());
        BigDecimal restPaysDe = driverPayout.min(maxRestContribution);
        BigDecimal custPaysDe = driverPayout.subtract(restPaysDe).max(BigDecimal.ZERO);
        BigDecimal totalCustomerDeliveryFee = custPaysDe.add(config.getFixedPlatformFee());
        java.util.Set<com.fooddelivery.order.entity.OrderCharge> charges = new java.util.HashSet<>();
        // 0. Platform pays Restaurant the Food Cost
        charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.FOOD_COST, com.fooddelivery.order.enums.ChargeEntityType.PLATFORM, com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT, foodCost));
        // 1. Customer pays Fixed Platform Fee
        if (config.getFixedPlatformFee().compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.PLATFORM_FIXED_FEE, com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER, com.fooddelivery.order.enums.ChargeEntityType.PLATFORM, config.getFixedPlatformFee()));
        }
        // 2. Customer pays Driver
        if (custPaysDe.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.DELIVERY_FEE, com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER, com.fooddelivery.order.enums.ChargeEntityType.DRIVER, custPaysDe));
        }
        // 3. Restaurant pays Fixed Platform Fee
        if (config.getFixedPlatformFee().compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.PLATFORM_FIXED_FEE, com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT, com.fooddelivery.order.enums.ChargeEntityType.PLATFORM, config.getFixedPlatformFee()));
        }
        // 4. Restaurant pays Driver
        if (restPaysDe.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.DELIVERY_FEE, com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT, com.fooddelivery.order.enums.ChargeEntityType.DRIVER, restPaysDe));
        }
        // 5. Restaurant pays Platform Bonus
        if (platformBonus.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.PLATFORM_BONUS, com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT, com.fooddelivery.order.enums.ChargeEntityType.PLATFORM, platformBonus));
        }
        BigDecimal sgstAmount = foodCost.multiply(config.getSgstPercent());
        BigDecimal cgstAmount = foodCost.multiply(config.getCgstPercent());
        // 6. Customer pays SGST to Restaurant
        if (sgstAmount.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.SGST, com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER, com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT, sgstAmount));
        }
        // 7. Customer pays CGST to Restaurant
        if (cgstAmount.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.CGST, com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER, com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT, cgstAmount));
        }
        return PricingBreakdown.builder().totalCustomerDeliveryFee(totalCustomerDeliveryFee.setScale(2, RoundingMode.HALF_UP)).sgst(sgstAmount.setScale(2, RoundingMode.HALF_UP)).cgst(cgstAmount.setScale(2, RoundingMode.HALF_UP)).charges(charges).build();
    }

    private com.fooddelivery.order.entity.OrderCharge createCharge(com.fooddelivery.common.enums.ChargeCategory category, com.fooddelivery.order.enums.ChargeEntityType payer, com.fooddelivery.order.enums.ChargeEntityType payee, BigDecimal amount) {
        return com.fooddelivery.order.entity.OrderCharge.builder().id(java.util.UUID.randomUUID()).category(category).payerType(payer).payeeType(payee).amount(amount.setScale(2, RoundingMode.HALF_UP)).build();
    }

    public BigDecimal getMinAmountForFreeDelivery(BigDecimal distanceKm) {
        BigDecimal effectiveDistance = distanceKm.max(BigDecimal.ONE);
        BigDecimal driverPayout = config.getBasePrice().add(effectiveDistance.multiply(config.getPerKmRate()));
        // For custPaysDe to be 0, maxRestContribution must be >= driverPayout
        // maxRestContribution = foodCost * restMaxContributionPercent
        // So, foodCost >= driverPayout / restMaxContributionPercent
        if (config.getRestMaxContributionPercent().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.valueOf(999999); // Cannot get free delivery if rest contribution is 0
        }
        return driverPayout.divide(config.getRestMaxContributionPercent(), 2, RoundingMode.CEILING);
    }

    @java.lang.SuppressWarnings("all")
    public DynamicPricingService(final DynamicPricingConfig config) {
        this.config = config;
    }
}
