package com.fooddelivery.order.ledger;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.fooddelivery.order.ledger.LedgerAccountResolver;
import com.fooddelivery.order.ledger.ResolvedAccount;
import com.fooddelivery.common.constants.LedgerAccounts;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.enums.ChargeEntityType;

import java.util.UUID;

public class LedgerAccountResolverTest {

    private final LedgerAccountResolver resolver = new LedgerAccountResolver();

    @Test
    void testResolvePlatform_Revenue() {
        Order order = new Order();
        ResolvedAccount acc = resolver.resolve(ChargeEntityType.PLATFORM, LedgerAccounts.PLATFORM_REVENUE, order);
        assertEquals(LedgerAccountType.PLATFORM_REVENUE, acc.getType());
        assertEquals(LedgerAccounts.PLATFORM_REVENUE, acc.getId());
    }

    @Test
    void testResolvePlatform_Clearing() {
        Order order = new Order();
        ResolvedAccount acc = resolver.resolve(ChargeEntityType.PLATFORM, null, order);
        assertEquals(LedgerAccountType.PLATFORM_CLEARING, acc.getType());
        assertEquals(LedgerAccounts.PLATFORM_CLEARING, acc.getId());
    }

    @Test
    void testResolveRestaurant() {
        Order order = new Order();
        order.setRestaurantId(UUID.randomUUID());
        ResolvedAccount acc = resolver.resolve(ChargeEntityType.RESTAURANT, null, order);
        assertEquals(LedgerAccountType.RESTAURANT_PAYABLE, acc.getType());
        assertEquals(order.getRestaurantId(), acc.getId());
    }

    @Test
    void testResolveDriver_MissingDriverId_ThrowsException() {
        Order order = new Order();
        assertThrows(IllegalStateException.class, () -> resolver.resolve(ChargeEntityType.DRIVER, null, order));
    }
}
