package com.fooddelivery.customer.service;

import com.fooddelivery.customer.config.DynamicPricingConfig;
import com.fooddelivery.customer.model.PricingBreakdown;
import com.fooddelivery.customer.model.PricingRates;
import com.fooddelivery.order.entity.OrderCharge;
import com.fooddelivery.order.enums.ChargeEntityType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The breakdown and the charge rows are two views of one calculation, and the ledger settles against
 * the charges while the customer is billed the breakdown's total. If they disagree the platform
 * either loses money or overcharges, silently.
 */
class PricingChargeConsistencyTest {

    private static PricingRates rates(String restContribution) {
        DynamicPricingConfig config = new DynamicPricingConfig();
        config.setBasePrice(new BigDecimal("20.00"));
        config.setPerKmRate(new BigDecimal("10.00"));
        config.setRestMaxContributionPercent(new BigDecimal(restContribution));
        config.setFixedPlatformFee(new BigDecimal("5.00"));
        config.setPlatformExcessCutPercent(new BigDecimal("0.50"));
        config.setSgstPercent(new BigDecimal("0.025"));
        config.setCgstPercent(new BigDecimal("0.025"));
        config.setDeliverySgstPercent(new BigDecimal("0.05"));
        config.setDeliveryCgstPercent(new BigDecimal("0.05"));
        return config.currentRates();
    }

    private static DynamicPricingService service() {
        return new DynamicPricingService(new DynamicPricingConfig());
    }

    private static BigDecimal customerPays(PricingBreakdown b) {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderCharge c : b.getCharges()) {
            if (c.getPayerType() == ChargeEntityType.CUSTOMER) {
                sum = sum.add(c.getAmount());
            }
        }
        return sum;
    }

    @Test
    void customerTotalEqualsFoodCostPlusEveryChargeTheCustomerPays() {
        BigDecimal foodCost = new BigDecimal("3728.99");
        PricingBreakdown b = service().calculatePricing(foodCost, new BigDecimal("5.00"), rates("0.20"));

        assertThat(b.getCustomerTotal())
                .as("what we bill must equal food cost plus the customer's own charge rows")
                .isEqualByComparingTo(foodCost.add(customerPays(b)));
    }

    @Test
    void customerTotalHoldsWhenTheRestaurantCoversTheWholeDeliveryFee() {
        // A high contribution percent drives the customer delivery fee to zero and opens a
        // platform bonus -- the branch where the arithmetic is easiest to get wrong.
        BigDecimal foodCost = new BigDecimal("900.00");
        PricingBreakdown b = service().calculatePricing(foodCost, new BigDecimal("2.00"), rates("0.50"));

        assertThat(b.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b.getPlatformBonus()).isGreaterThan(BigDecimal.ZERO);
        assertThat(b.getCustomerTotal()).isEqualByComparingTo(foodCost.add(customerPays(b)));
    }

    @Test
    void replayingTheSameInputsAndRatesReproducesTheSameTotal() {
        // The property the binding quote depends on: checkout replays the stored inputs and rates
        // and must land on the number the customer was shown.
        PricingRates snapshot = rates("0.15");
        BigDecimal foodCost = new BigDecimal("450.00");
        BigDecimal distance = new BigDecimal("3.50");

        PricingBreakdown quoted = service().calculatePricing(foodCost, distance, snapshot);
        PricingBreakdown replayed = service().calculatePricing(foodCost, distance, snapshot);

        assertThat(replayed.getCustomerTotal()).isEqualByComparingTo(quoted.getCustomerTotal());
        assertThat(replayed.getRestaurantPayout()).isEqualByComparingTo(quoted.getRestaurantPayout());
        assertThat(replayed.getDriverNetPayout()).isEqualByComparingTo(quoted.getDriverNetPayout());
        assertThat(replayed.getCharges()).hasSameSizeAs(quoted.getCharges());
    }

    @Test
    void aContributionPercentBeyondTheCeilingIsRejectedRatherThanBillingTheRestaurant() {
        // restaurantPayout is deliberately unclamped (I-24b), so an out-of-range percent would
        // quietly produce a negative payout instead of failing.
        assertThatThrownBy(() -> rates("0.90"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the maximum");
    }
}
