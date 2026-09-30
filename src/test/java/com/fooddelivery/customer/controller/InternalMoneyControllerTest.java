package com.fooddelivery.customer.controller;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalMoneyControllerTest {

    private static final UUID DRIVER_ID = UUID.fromString("e1000000-0000-4000-8000-000000000001");
    private static final UUID OWNED_ORDER_ID = UUID.fromString("e1000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_ORDER_ID = UUID.fromString("e1000000-0000-4000-8000-000000000003");

    @Mock
    private IOrderRepository orderRepository;

    @InjectMocks
    private InternalMoneyController controller;

    @Test
    void canonicalBatchRouteReturnsOnlyOrdersOwnedByTheRequestedDriver() {
        when(orderRepository.findById(OWNED_ORDER_ID)).thenReturn(Optional.of(orderFor(DRIVER_ID, OWNED_ORDER_ID)));
        when(orderRepository.findById(OTHER_ORDER_ID)).thenReturn(Optional.of(orderFor(UUID.randomUUID(), OTHER_ORDER_ID)));

        var response = controller.fetchDriverOrderMoneyBatch(DRIVER_ID,
                List.of(OWNED_ORDER_ID.toString(), OTHER_ORDER_ID.toString(), "not-a-uuid"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getOrderId()).isEqualTo(OWNED_ORDER_ID);
        assertThat(response.getBody().get(0).getDriverId()).isEqualTo(DRIVER_ID);
    }

    @Test
    @SuppressWarnings("removal")
    void legacyBatchAliasDelegatesToTheCanonicalImplementationDuringRollingDeployment() {
        when(orderRepository.findById(OWNED_ORDER_ID)).thenReturn(Optional.of(orderFor(DRIVER_ID, OWNED_ORDER_ID)));

        var response = controller.fetchDriverOrderMoneyBatchLegacy(DRIVER_ID, List.of(OWNED_ORDER_ID.toString()));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).extracting(earning -> earning.getOrderId())
                .containsExactly(OWNED_ORDER_ID);
    }

    private static Order orderFor(UUID driverId, UUID orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setDeliveryExecutiveId(driverId);
        order.setDriverGrossPayout(new BigDecimal("100.00"));
        order.setDriverTaxes(new BigDecimal("10.00"));
        order.setDriverNetPayout(new BigDecimal("90.00"));
        order.setDeliveryFee(new BigDecimal("60.00"));
        order.setRestaurantDeliveryContribution(new BigDecimal("30.00"));
        order.setPlatformBonus(new BigDecimal("10.00"));
        return order;
    }
}
