package com.fooddelivery.customer.dto;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrderResponseLeakTest {

    @Test
    void testOrderResponseDoesNotLeakOtherPartyFinancials() {
        List<String> leakedFields = Arrays.asList(
            "restaurantPayout", "platformBonus", "restaurantPlatformFee",
            "restaurantDeliveryContribution", "driverGrossPayout", "driverTaxes",
            "driverNetPayout", "foodCost"
        );

        Field[] fields = OrderResponse.class.getDeclaredFields();
        for (Field field : fields) {
            assertFalse(leakedFields.contains(field.getName()), 
                "OrderResponse must not leak financial field: " + field.getName());
        }
    }
}
