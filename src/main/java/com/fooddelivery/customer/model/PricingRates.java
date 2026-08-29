package com.fooddelivery.customer.model;

import java.math.BigDecimal;

/**
 * The rate set applied to one pricing calculation.
 *
 * <p>Pricing must be reproducible long after the fact: a restaurant disputing a payout is disputing
 * the rates that were in force when the order was placed, not the ones in force today.
 * {@code DynamicPricingConfig} is {@code @RefreshScope}, so the live values can change at any moment
 * — including between a customer seeing a quote and placing the order. Capturing the rates once and
 * replaying them makes the calculation a pure function of data we have stored.
 *
 * <p>This is also the seam for per-restaurant commercial terms: a negotiated
 * {@code restMaxContributionPercent} becomes a different value in this record, with no change to the
 * arithmetic or to any call site.
 */
public record PricingRates(
        BigDecimal basePrice,
        BigDecimal perKmRate,
        BigDecimal restMaxContributionPercent,
        BigDecimal fixedPlatformFee,
        BigDecimal platformExcessCutPercent,
        BigDecimal sgstPercent,
        BigDecimal cgstPercent,
        BigDecimal deliverySgstPercent,
        BigDecimal deliveryCgstPercent) {

    /** Highest share of food cost a restaurant may contribute toward delivery. */
    public static final BigDecimal MAX_REST_CONTRIBUTION_PERCENT = new BigDecimal("0.50");

    public PricingRates {
        require(basePrice, "basePrice");
        require(perKmRate, "perKmRate");
        require(restMaxContributionPercent, "restMaxContributionPercent");
        require(fixedPlatformFee, "fixedPlatformFee");
        require(platformExcessCutPercent, "platformExcessCutPercent");
        require(sgstPercent, "sgstPercent");
        require(cgstPercent, "cgstPercent");
        require(deliverySgstPercent, "deliverySgstPercent");
        require(deliveryCgstPercent, "deliveryCgstPercent");

        // restaurantPayout is deliberately not clamped at zero (I-24b), so an out-of-range
        // contribution percent would silently bill the restaurant instead of failing.
        if (restMaxContributionPercent.compareTo(MAX_REST_CONTRIBUTION_PERCENT) > 0) {
            throw new IllegalArgumentException(
                    "restMaxContributionPercent " + restMaxContributionPercent
                            + " exceeds the maximum of " + MAX_REST_CONTRIBUTION_PERCENT);
        }
        if (platformExcessCutPercent.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "platformExcessCutPercent cannot exceed 1: " + platformExcessCutPercent);
        }
    }

    private static void require(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("Pricing rate " + name + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Pricing rate " + name + " cannot be negative: " + value);
        }
    }
}
