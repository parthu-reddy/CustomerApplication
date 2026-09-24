package com.fooddelivery.customer.service;

import java.util.Collection;

/**
 * The prep time a new order is promised with: the longest of the restaurant's own default, the
 * slowest dish in the order, and a 15-minute floor.
 *
 * <p>The outlet default ({@code Outlet.defaultPrepTimeSeconds}, edited in the restaurant's
 * settings) used to be stored and displayed and read by nothing -- orders took the slowest dish
 * alone. A kitchen that knows it runs slow (a rush, short staff) now raises one number and every
 * new order is promised that much. RestaurentApplication's accept adds its own extra minutes on
 * top of this figure.
 */
final class OrderPrepTime {

    static final int FLOOR_MINUTES = 15;

    private OrderPrepTime() {
    }

    /**
     * @param outletDefaultSeconds the restaurant API's {@code defaultPrepTimeSeconds} as decoded
     *                             from JSON (any Number, or null when absent)
     * @param itemMinutes          each quoted line's prep minutes; nulls are ignored
     */
    static int minutes(Object outletDefaultSeconds, Collection<Integer> itemMinutes) {
        int result = FLOOR_MINUTES;
        if (outletDefaultSeconds instanceof Number n && n.longValue() > 0) {
            // Rounded up: a 20.5-minute default is not a 20-minute promise.
            result = Math.max(result, (int) Math.ceil(n.doubleValue() / 60.0));
        }
        if (itemMinutes != null) {
            for (Integer m : itemMinutes) {
                if (m != null && m > result) result = m;
            }
        }
        return result;
    }
}
