package com.fooddelivery.order.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.event.OrderPaidEvent;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Every field the restaurant and the delivery service read off ORDER_PAID is actually populated.
 *
 * <p>`customerId` was declared nowhere on the event, so `RestaurantOrder.customer_id` was NULL for
 * every order the platform had ever taken, and nothing noticed because nothing read it back. The
 * reflective check is the point: a field added to `OrderPaidEvent` and not set in the builder fails
 * here rather than becoming another permanently-null column.
 */
class OrderPaidEventCompletenessTest {

    private OutboxEventRepository outbox;
    private OrderActionService actionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxEventRepository.class);
        actionService = new OrderActionService(mock(IOrderRepository.class), outbox,
                mock(IPaymentIntentRepository.class), objectMapper);
    }

    private Order fullyPopulatedOrder(PaymentMethod method) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCustomerId(UUID.randomUUID());
        o.setCustomerName("Ada");
        o.setRestaurantId(UUID.randomUUID());
        o.setPaymentMethod(method);
        o.setEstimatedPrepTimeMinutes(20);
        o.setDeliveryLat(12.9);
        o.setDeliveryLng(77.6);
        o.setDeliveryAddress("1 Test Road");
        o.setPickupOtp("111111");
        o.setOtp("222222");
        o.setTotalAmount(new BigDecimal("420.00"));
        o.setItemTotal(new BigDecimal("300.00"));
        o.setRestaurantPlatformFee(new BigDecimal("10.00"));
        o.setRestaurantDeliveryContribution(new BigDecimal("20.00"));
        o.setPlatformBonus(new BigDecimal("5.00"));
        o.setRestaurantPayout(new BigDecimal("265.00"));
        Set<OrderItem> items = new HashSet<>();
        items.add(OrderItem.builder().id(UUID.randomUUID()).menuItemId(UUID.randomUUID())
                .name("Dosa").quantity(1).price(new BigDecimal("300.00")).build());
        o.setOrderItems(items);
        return o;
    }

    private OrderPaidEvent emitted(EventType expectedType) throws Exception {
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outbox).save(captor.capture());
        assertEquals(expectedType, captor.getValue().getEventType());
        return objectMapper.readValue(captor.getValue().getPayload(), OrderPaidEvent.class);
    }

    @Test
    void everyDeclaredFieldIsPopulated() throws Exception {
        actionService.emitOrderPaidEvent(fullyPopulatedOrder(PaymentMethod.CARD));
        OrderPaidEvent event = emitted(EventType.ORDER_PAID);

        List<String> unset = new java.util.ArrayList<>();
        for (Field f : OrderPaidEvent.class.getDeclaredFields()) {
            if (f.isSynthetic()) {
                continue;
            }
            f.setAccessible(true);
            if (f.get(event) == null) {
                unset.add(f.getName());
            }
        }
        assertTrue(unset.isEmpty(),
                "fields declared on OrderPaidEvent but never set by the builder: " + unset);
    }

    @Test
    void theCodEventCarriesTheSameFields() throws Exception {
        actionService.emitOrderPlacedCodEvent(fullyPopulatedOrder(PaymentMethod.COD));
        OrderPaidEvent event = emitted(EventType.ORDER_PLACED_COD);

        assertEquals(PaymentMethod.COD, event.getPaymentMethod());
        assertNotNull(event.getCustomerId());
        assertNotNull(event.getPickupOtp());
        assertNotNull(event.getRestaurantPayout());
    }

    @Test
    void theTwoEventsDifferOnlyInTypeAndMethod() throws Exception {
        Order paid = fullyPopulatedOrder(PaymentMethod.CARD);
        actionService.emitOrderPaidEvent(paid);
        OrderPaidEvent a = emitted(EventType.ORDER_PAID);

        setUp();
        Order cod = fullyPopulatedOrder(PaymentMethod.COD);
        cod.setId(paid.getId());
        cod.setCustomerId(paid.getCustomerId());
        cod.setRestaurantId(paid.getRestaurantId());
        cod.setOrderItems(paid.getOrderItems());
        actionService.emitOrderPlacedCodEvent(cod);
        OrderPaidEvent b = emitted(EventType.ORDER_PLACED_COD);

        assertEquals(a.getOrderId(), b.getOrderId());
        assertEquals(a.getCustomerId(), b.getCustomerId());
        assertEquals(a.getRestaurantPayout(), b.getRestaurantPayout());
        assertNotEquals(a.getPaymentMethod(), b.getPaymentMethod());
    }

    @Test
    void theCustomerIdIsCarried() throws Exception {
        Order o = fullyPopulatedOrder(PaymentMethod.CARD);
        actionService.emitOrderPaidEvent(o);

        assertEquals(o.getCustomerId(), emitted(EventType.ORDER_PAID).getCustomerId(),
                "this was absent entirely, so RestaurantOrder.customer_id was always NULL");
    }
}
