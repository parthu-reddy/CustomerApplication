package com.fooddelivery.customer.service;

import com.fooddelivery.customer.config.DynamicPricingConfig;
import com.fooddelivery.customer.model.PricingBreakdown;
import com.fooddelivery.customer.model.PricingRates;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class DynamicPricingService {
    

    private final DynamicPricingConfig config;

    /** Snapshot of the rates currently in force. Capture once per order flow, then replay it. */
    public PricingRates currentRates() {
        return config.currentRates();
    }

    /** Every monetary value in the platform is carried at 2dp. */
    private static final int MONEY_SCALE = 2;

    /**
     * Rounds a quantity at the point it is decided. Values derived purely by adding or
     * subtracting already-rounded quantities are exact and must NOT be rounded again --
     * that is what made the charges and the breakdown disagree (I-23).
     */
    private static BigDecimal round(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Prices one order against an explicit rate snapshot.
     *
     * <p>Pure in its arguments: the same inputs and the same rates always produce the same
     * breakdown and the same charges. That is what lets a stored quote be replayed at checkout and
     * be guaranteed to agree with what the customer was shown.
     */
    public PricingBreakdown calculatePricing(BigDecimal foodCost, BigDecimal distanceKm, PricingRates rates) {
        if (rates == null) {
            throw new IllegalArgumentException("Pricing rates are required");
        }
        if (foodCost == null || foodCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Food cost cannot be null or negative");
        }
        if (distanceKm == null || distanceKm.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Distance cannot be null or negative for pricing calculations");
        }
        // Inputs are normalised to 2dp so every derived value stays exact at 2dp.
        foodCost = round(foodCost);
        final BigDecimal fixedPlatformFee = round(rates.fixedPlatformFee());

        BigDecimal effectiveDistance = distanceKm.max(BigDecimal.ONE);
        // decided by multiplication -> round
        BigDecimal driverPayout = round(rates.basePrice().add(effectiveDistance.multiply(rates.perKmRate())));
        BigDecimal maxRestContribution = distanceKm.compareTo(BigDecimal.valueOf(5.0)) > 0
            ? BigDecimal.ZERO.setScale(MONEY_SCALE)
            : round(foodCost.multiply(rates.restMaxContributionPercent()));
        // derived from rounded values -> already exact
        BigDecimal excessBudget = maxRestContribution.subtract(driverPayout).max(BigDecimal.ZERO);
        BigDecimal platformBonus = round(excessBudget.multiply(rates.platformExcessCutPercent()));
        BigDecimal restPaysDe = driverPayout.min(maxRestContribution);
        BigDecimal custPaysDe = driverPayout.subtract(restPaysDe).max(BigDecimal.ZERO);
        BigDecimal totalCustomerDeliveryFee = custPaysDe.add(fixedPlatformFee);
        java.util.Set<com.fooddelivery.order.entity.OrderCharge> charges = new java.util.HashSet<>();
        // 0. Platform pays Restaurant the Food Cost
        charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.FOOD_COST, com.fooddelivery.order.enums.ChargeEntityType.PLATFORM, com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT, foodCost));
        // 1. Customer pays Fixed Platform Fee
        if (fixedPlatformFee.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.PLATFORM_FIXED_FEE, com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER, com.fooddelivery.order.enums.ChargeEntityType.PLATFORM, fixedPlatformFee));
        }
        // 2. Customer pays Driver
        if (custPaysDe.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.DELIVERY_FEE, com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER, com.fooddelivery.order.enums.ChargeEntityType.DRIVER, custPaysDe));
        }
        // 3. Restaurant pays Fixed Platform Fee
        if (fixedPlatformFee.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.PLATFORM_FIXED_FEE, com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT, com.fooddelivery.order.enums.ChargeEntityType.PLATFORM, fixedPlatformFee));
        }
        // 4. Restaurant pays Driver
        if (restPaysDe.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.DELIVERY_FEE, com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT, com.fooddelivery.order.enums.ChargeEntityType.DRIVER, restPaysDe));
        }
        // 5. Restaurant pays Platform Bonus
        if (platformBonus.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.PLATFORM_BONUS, com.fooddelivery.order.enums.ChargeEntityType.RESTAURANT, com.fooddelivery.order.enums.ChargeEntityType.PLATFORM, platformBonus));
        }
        BigDecimal sgstAmount = round(foodCost.multiply(rates.sgstPercent()));
        BigDecimal cgstAmount = round(foodCost.multiply(rates.cgstPercent()));
        // 6. Customer pays SGST to Government
        if (sgstAmount.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.SGST, com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER, com.fooddelivery.order.enums.ChargeEntityType.GOVERNMENT, sgstAmount));
        }
        // 7. Customer pays CGST to Government
        if (cgstAmount.compareTo(BigDecimal.ZERO) > 0) {
            charges.add(createCharge(com.fooddelivery.common.enums.ChargeCategory.CGST, com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER, com.fooddelivery.order.enums.ChargeEntityType.GOVERNMENT, cgstAmount));
        }
        
        BigDecimal driverSgstAmount = round(driverPayout.multiply(rates.deliverySgstPercent()));
        BigDecimal driverCgstAmount = round(driverPayout.multiply(rates.deliveryCgstPercent()));
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
        // I-24b: no .max(ZERO) clamp. A negative payout is a real debit against the
        // restaurant and is carried as such, so the charges and the payout agree.
        BigDecimal restaurantPayout = foodCost.subtract(fixedPlatformFee).subtract(restPaysDe).subtract(platformBonus);

        // The single definition of what the customer pays. Previously each call site added these
        // four up itself, which is exactly where the quote and the charge could drift apart.
        BigDecimal customerTotal = foodCost.add(totalCustomerDeliveryFee).add(sgstAmount).add(cgstAmount);

        return PricingBreakdown.builder()
            .customerTotal(customerTotal)
            .appliedRates(rates)
            .totalCustomerDeliveryFee(totalCustomerDeliveryFee)
            .itemTotal(foodCost)
            .customerPlatformFee(fixedPlatformFee)
            .restaurantPlatformFee(fixedPlatformFee)
            .platformBonus(platformBonus)
            .restaurantDeliveryContribution(restPaysDe)
            .restaurantPayout(restaurantPayout)
            .deliveryFee(custPaysDe)
            .driverGrossPayout(driverPayout)
            .driverTaxes(driverTaxes)
            .driverNetPayout(driverNetPayout)
            .sgst(sgstAmount)
            .cgst(cgstAmount)
            .charges(charges)
            .build();
    }

    private com.fooddelivery.order.entity.OrderCharge createCharge(com.fooddelivery.common.enums.ChargeCategory category, com.fooddelivery.order.enums.ChargeEntityType payer, com.fooddelivery.order.enums.ChargeEntityType payee, BigDecimal amount) {
        return com.fooddelivery.order.entity.OrderCharge.builder().id(java.util.UUID.randomUUID()).category(category).payerType(payer).payeeType(payee).amount(amount).build();
    }

    public java.util.Optional<BigDecimal> getMinAmountForFreeDelivery(BigDecimal distanceKm, PricingRates rates) {
        if (distanceKm == null || distanceKm.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Distance cannot be null or negative for pricing calculations");
        }
        BigDecimal effectiveDistance = distanceKm.max(BigDecimal.ONE);
        BigDecimal driverPayout = rates.basePrice().add(effectiveDistance.multiply(rates.perKmRate()));
        if (distanceKm.compareTo(BigDecimal.valueOf(5.0)) > 0) {
            return java.util.Optional.empty();
        }
        if (rates.restMaxContributionPercent().compareTo(BigDecimal.ZERO) <= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(driverPayout.divide(rates.restMaxContributionPercent(), 2, RoundingMode.CEILING));
    }

    
}
