package com.fooddelivery.order.service.state;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.customer.mapper.OrderMapper;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.service.state.impl.ReadyForPickupState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pickup time is what the customer's arrival estimate counts from once the rider has the food. */
@ExtendWith(MockitoExtension.class)
class HandoverTimeStateTest {

    @Mock
    private OrderActionService actionService;

    @Mock
    private com.fooddelivery.order.ledger.LedgerBookkeeper ledgerBookkeeper;

    @Test
    void handoverRecordsWhen_andTheResponseEstimatesArrivalFromIt() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("150.00"));
        order.setSgst(BigDecimal.ZERO);
        order.setCgst(BigDecimal.ZERO);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setCustomerPlatformFee(BigDecimal.ZERO);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        order.setDeliveryTravelSeconds(600);

        long before = System.currentTimeMillis();
        new ReadyForPickupState().handleOrderHandedOver(
                new OrderContext(order, null, actionService, ledgerBookkeeper, "UNKNOWN", order.getPaymentMethod()));

        assertEquals(OrderStatus.HANDED_OVER, order.getStatus());
        assertNotNull(order.getHandedOverAt());
        assertTrue(order.getHandedOverAt() >= before);
        Long eta = OrderMapper.mapToResponse(order).getEstimatedArrivalTime();
        assertNotNull(eta);
        assertTrue(eta >= order.getHandedOverAt() + 600_000 - 1);
    }
}
