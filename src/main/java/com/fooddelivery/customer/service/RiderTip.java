package com.fooddelivery.customer.service;

import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.order.entity.OrderCharge;
import com.fooddelivery.order.enums.ChargeEntityType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * The customer's tip for the rider (Phase 7 A3).
 *
 * <p>Booked as a CUSTOMER -> DRIVER charge, so it reaches the rider through the same DELIVERED
 * ledger transaction as the delivery fee, in full: no platform cut, no GST (a tip is not a
 * supply). Its category is DELIVERY_FEE with the description "Rider tip" rather than a new TIP
 * category: LedgerService decodes categories with the common enum and could not be released
 * alongside this change, and a category it cannot read would fail the whole DELIVERED booking --
 * every payout on the order, not just the tip. A dedicated category is a follow-up.
 */
public final class RiderTip {

    public static final BigDecimal MAX = new BigDecimal("500");
    public static final String DESCRIPTION = "Rider tip";

    private RiderTip() {
    }

    /** The tip as charged: 0 when absent, whole rupees, 0..500. Request validation already enforces the range; this is the backstop. */
    public static BigDecimal of(BigDecimal requested) {
        if (requested == null) return BigDecimal.ZERO.setScale(2);
        if (requested.signum() < 0 || requested.compareTo(MAX) > 0 || requested.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("A tip must be whole rupees from 0 to " + MAX);
        }
        return requested.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** The ledger charge for a tip, or null for none. */
    public static OrderCharge charge(BigDecimal tip) {
        if (tip == null || tip.signum() <= 0) return null;
        return OrderCharge.builder()
                .id(UUID.randomUUID())
                .category(ChargeCategory.DELIVERY_FEE)
                .payerType(ChargeEntityType.CUSTOMER)
                .payeeType(ChargeEntityType.DRIVER)
                .amount(tip)
                .description(DESCRIPTION)
                .build();
    }
}
