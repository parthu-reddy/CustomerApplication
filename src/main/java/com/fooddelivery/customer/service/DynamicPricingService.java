package com.fooddelivery.customer.service;

import com.fooddelivery.customer.config.DynamicPricingConfig;
import com.fooddelivery.customer.model.PricingBreakdown;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@lombok.extern.slf4j.Slf4j
public class DynamicPricingService {
    

    private final DynamicPricingConfig config;

    public PricingBreakdown calculatePricing(BigDecimal foodCost, BigDecimal distanceKm) {
        if (foodCost == null || foodCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Food cost cannot be null or negative");
        }
        if (distanceKm == null || distanceKm.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Distance cannot be null or negative for pricing calculations");
        }
        BigDecimal effectiveDistance = distanceKm.max(BigDecimal.ONE);
        BigDecimal driverPayout = config.getBasePrice().add(effectiveDistance.multiply(config.getPerKmRate()));
        BigDecimal maxRestContribution = distanceKm.compareTo(BigDecimal.valueOf(5.0)) > 0 
            ? BigDecimal.ZERO 
            : foodCost.multiply(config.getRestMaxContributionPercent());
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
        // 6. Customer pays SGST to Government
        if (sgstAmount.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.SGST, com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER, com.fooddelivery.order.enums.ChargeEntityType.GOVERNMENT, sgstAmount));
        }
        // 7. Customer pays CGST to Government
        if (cgstAmount.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.CGST, com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER, com.fooddelivery.order.enums.ChargeEntityType.GOVERNMENT, cgstAmount));
        }
        
        BigDecimal driverSgstAmount = driverPayout.multiply(config.getDeliverySgstPercent());
        BigDecimal driverCgstAmount = driverPayout.multiply(config.getDeliveryCgstPercent());
        // 8. Driver pays SGST to Government
        if (driverSgstAmount.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.SGST, com.fooddelivery.order.enums.ChargeEntityType.DRIVER, com.fooddelivery.order.enums.ChargeEntityType.GOVERNMENT, driverSgstAmount));
        }
        // 9. Driver pays CGST to Government
        if (driverCgstAmount.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.CGST, com.fooddelivery.order.enums.ChargeEntityType.DRIVER, com.fooddelivery.order.enums.ChargeEntityType.GOVERNMENT, driverCgstAmount));
        }

        BigDecimal driverTaxes = driverSgstAmount.add(driverCgstAmount);
        BigDecimal driverNetPayout = driverPayout.subtract(driverTaxes);
        BigDecimal restaurantPayout = foodCost.subtract(config.getFixedPlatformFee()).subtract(restPaysDe).subtract(platformBonus).max(BigDecimal.ZERO);

        return PricingBreakdown.builder()
            .totalCustomerDeliveryFee(totalCustomerDeliveryFee.setScale(2, RoundingMode.HALF_UP))
            .itemTotal(foodCost.setScale(2, RoundingMode.HALF_UP))
            .customerPlatformFee(config.getFixedPlatformFee().setScale(2, RoundingMode.HALF_UP))
            .restaurantPlatformFee(config.getFixedPlatformFee().setScale(2, RoundingMode.HALF_UP))
            .platformBonus(platformBonus.setScale(2, RoundingMode.HALF_UP))
            .restaurantDeliveryContribution(restPaysDe.setScale(2, RoundingMode.HALF_UP))
            .restaurantPayout(restaurantPayout.setScale(2, RoundingMode.HALF_UP))
            .deliveryFee(custPaysDe.setScale(2, RoundingMode.HALF_UP))
            .driverGrossPayout(driverPayout.setScale(2, RoundingMode.HALF_UP))
            .driverTaxes(driverTaxes.setScale(2, RoundingMode.HALF_UP))
            .driverNetPayout(driverNetPayout.setScale(2, RoundingMode.HALF_UP))
            .sgst(sgstAmount.setScale(2, RoundingMode.HALF_UP))
            .cgst(cgstAmount.setScale(2, RoundingMode.HALF_UP))
            .charges(charges)
            .build();
    }

    private com.fooddelivery.order.entity.OrderCharge createCharge(com.fooddelivery.common.enums.ChargeCategory category, com.fooddelivery.order.enums.ChargeEntityType payer, com.fooddelivery.order.enums.ChargeEntityType payee, BigDecimal amount) {
        return com.fooddelivery.order.entity.OrderCharge.builder().id(java.util.UUID.randomUUID()).category(category).payerType(payer).payeeType(payee).amount(amount.setScale(2, RoundingMode.HALF_UP)).build();
    }

    public java.util.Optional<BigDecimal> getMinAmountForFreeDelivery(BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Distance cannot be null or negative for pricing calculations");
        }
        BigDecimal effectiveDistance = distanceKm.max(BigDecimal.ONE);
        BigDecimal driverPayout = config.getBasePrice().add(effectiveDistance.multiply(config.getPerKmRate()));
        if (distanceKm.compareTo(BigDecimal.valueOf(5.0)) > 0) {
            return java.util.Optional.empty();
        }
        if (config.getRestMaxContributionPercent().compareTo(BigDecimal.ZERO) <= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(driverPayout.divide(config.getRestMaxContributionPercent(), 2, RoundingMode.CEILING));
    }

    
    public DynamicPricingService(final DynamicPricingConfig config) {
        this.config = config;
    }
}
