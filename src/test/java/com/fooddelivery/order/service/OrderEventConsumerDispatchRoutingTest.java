package com.fooddelivery.order.service;

import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.OrderState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventConsumerDispatchRoutingTest {

    @SuppressWarnings("unchecked")
    @Test
    void dispatchFailedIsRoutedToTheStateHandler() throws Exception {
        Field handlersField = OrderEventConsumer.class.getDeclaredField("EVENT_HANDLERS");
        handlersField.setAccessible(true);
        Map<String, BiConsumer<OrderState, OrderContext>> handlers =
                (Map<String, BiConsumer<OrderState, OrderContext>>) handlersField.get(null);
        BiConsumer<OrderState, OrderContext> handler = handlers.get(EventType.DISPATCH_FAILED.name());
        assertNotNull(handler);

        OrderState state = mock(OrderState.class);
        OrderContext context = mock(OrderContext.class);
        handler.accept(state, context);

        verify(state).handleDispatchFailed(context);
    }
}
