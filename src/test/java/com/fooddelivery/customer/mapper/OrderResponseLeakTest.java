package com.fooddelivery.customer.mapper;

import org.junit.jupiter.api.Test;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.customer.dto.OrderResponse;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrderResponseLeakTest {

    @Test
    void testOrderResponseDoesNotLeakPayoutFields() {
        Order order = new Order();
        order.setId(java.util.UUID.fromString("54019a2f-1a98-4444-8888-000000000000"));
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setSgst(BigDecimal.ZERO);
        order.setCgst(BigDecimal.ZERO);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setCustomerPlatformFee(BigDecimal.ZERO);
        
        OrderResponse response = OrderMapper.mapToResponse(order);
        assertNotNull(response);
        
        java.lang.reflect.Field[] fields = OrderResponse.class.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("payout"), "OrderResponse should not contain payout fields like " + field.getName());
            assertFalse(name.contains("platformbonus"), "OrderResponse should not contain bonus fields like " + field.getName());
            assertFalse(name.contains("restaurantplatformfee"), "OrderResponse should not contain internal fee fields like " + field.getName());
        }
    }
}
