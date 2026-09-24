package com.fooddelivery.customer.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderPrepTimeTest {

    @Test
    void theOutletDefaultRaisesTheFloor() {
        // 25 min default, dishes at 10 and 12: the kitchen's own number wins.
        assertEquals(25, OrderPrepTime.minutes(1500, List.of(10, 12)));
    }

    @Test
    void aSlowerDishStillWins() {
        assertEquals(40, OrderPrepTime.minutes(1500, List.of(10, 40)));
    }

    @Test
    void fifteenMinutesStaysTheFloor() {
        assertEquals(15, OrderPrepTime.minutes(300, List.of(5)));
        assertEquals(15, OrderPrepTime.minutes(null, List.of()));
    }

    @Test
    void partMinutesRoundUpAndJsonNumberTypesAreAccepted() {
        assertEquals(21, OrderPrepTime.minutes(1230L, List.of()));
        assertEquals(21, OrderPrepTime.minutes(1230.0, List.of()));
    }

    @Test
    void missingOrBadValuesAreIgnored() {
        assertEquals(18, OrderPrepTime.minutes("1500", Arrays.asList(null, 18)));
        assertEquals(15, OrderPrepTime.minutes(-60, null));
    }
}
