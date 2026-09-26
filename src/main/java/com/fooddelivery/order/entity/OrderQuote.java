package com.fooddelivery.order.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A price the customer was shown, held so that checkout charges that exact price.
 *
 * <p>Before this existed, {@code createOrder} recomputed the price from scratch and the quote was
 * advisory. Three inputs could move in between — the cached delivery distance (which has a fallback
 * path), menu item prices, and the {@code @RefreshScope} rate config — so the customer could agree
 * to one number and be charged another, with nothing detecting the difference.
 *
 * <p>The quote stores the pricing <em>inputs</em> and the <em>rate snapshot</em> rather than only
 * the output. Because {@code DynamicPricingService.calculatePricing} is pure in those arguments,
 * checkout replays it and is guaranteed to reproduce the quoted figures, including the charge rows
 * that drive the ledger. {@link #quotedCustomerTotal} is kept solely to assert that it did.
 *
 * <p>Single-use: {@link #consumedAt} is claimed with a conditional update, so concurrent checkouts
 * on one quote cannot both succeed.
 */
@Entity
@Table(name = "order_quotes")
@lombok.Getter
@lombok.Setter
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OrderQuote {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "delivery_address_id", nullable = false)
    private UUID deliveryAddressId;

    /** Pricing input: the summed food cost the quote was calculated from. */
    @Column(name = "item_total", nullable = false)
    private BigDecimal itemTotal;

    /** Pricing input: the distance resolved when the quote was issued. */
    @Column(name = "distance_km", nullable = false)
    private BigDecimal distanceKm;

    /** Output, retained only to verify the replay reproduces it. */
    @Column(name = "quoted_customer_total", nullable = false)
    private BigDecimal quotedCustomerTotal;

    @Column(name = "rate_base_price", nullable = false)
    private BigDecimal rateBasePrice;
    @Column(name = "rate_per_km", nullable = false)
    private BigDecimal ratePerKm;
    @Column(name = "rate_rest_max_contribution_percent", nullable = false)
    private BigDecimal rateRestMaxContributionPercent;
    @Column(name = "rate_fixed_platform_fee", nullable = false)
    private BigDecimal rateFixedPlatformFee;
    @Column(name = "rate_platform_excess_cut_percent", nullable = false)
    private BigDecimal ratePlatformExcessCutPercent;
    @Column(name = "rate_sgst_percent", nullable = false)
    private BigDecimal rateSgstPercent;
    @Column(name = "rate_cgst_percent", nullable = false)
    private BigDecimal rateCgstPercent;
    @Column(name = "rate_delivery_sgst_percent", nullable = false)
    private BigDecimal rateDeliverySgstPercent;
    @Column(name = "rate_delivery_cgst_percent", nullable = false)
    private BigDecimal rateDeliveryCgstPercent;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = jakarta.persistence.FetchType.EAGER)
    @lombok.Builder.Default
    private Set<OrderQuoteItem> items = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Non-null once redeemed. Claimed atomically; a second checkout on the same quote fails. */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "consumed_order_id")
    private UUID consumedOrderId;

    public com.fooddelivery.customer.model.PricingRates appliedRates() {
        return new com.fooddelivery.customer.model.PricingRates(
                rateBasePrice,
                ratePerKm,
                rateRestMaxContributionPercent,
                rateFixedPlatformFee,
                ratePlatformExcessCutPercent,
                rateSgstPercent,
                rateCgstPercent,
                rateDeliverySgstPercent,
                rateDeliveryCgstPercent);
    }

    public void applyRates(com.fooddelivery.customer.model.PricingRates rates) {
        this.rateBasePrice = rates.basePrice();
        this.ratePerKm = rates.perKmRate();
        this.rateRestMaxContributionPercent = rates.restMaxContributionPercent();
        this.rateFixedPlatformFee = rates.fixedPlatformFee();
        this.ratePlatformExcessCutPercent = rates.platformExcessCutPercent();
        this.rateSgstPercent = rates.sgstPercent();
        this.rateCgstPercent = rates.cgstPercent();
        this.rateDeliverySgstPercent = rates.deliverySgstPercent();
        this.rateDeliveryCgstPercent = rates.deliveryCgstPercent();
    }
}
