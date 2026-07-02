package com.fooddelivery.order.service.state;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.service.state.impl.*;

import java.util.EnumMap;
import java.util.Map;

public class OrderStateFactory {

    private static final Map<OrderStatus, OrderState> stateMap = new EnumMap<>(OrderStatus.class);
    private static final TerminalState terminalState = new TerminalState();

    static {
        stateMap.put(OrderStatus.CREATED, new CreatedState());
        stateMap.put(OrderStatus.PAID, new PaidState());
        stateMap.put(OrderStatus.AWAITING_DELAY_APPROVAL, new AwaitingDelayApprovalState());
        stateMap.put(OrderStatus.ACCEPTED, new AcceptedState());
        stateMap.put(OrderStatus.READY_FOR_PICKUP, new ReadyForPickupState());
        stateMap.put(OrderStatus.DISPATCHED, new DispatchedState());
        stateMap.put(OrderStatus.OUT_FOR_DELIVERY, new OutForDeliveryState());
        
        // Terminal states
        stateMap.put(OrderStatus.DELIVERED, terminalState);
        stateMap.put(OrderStatus.CANCELLED, terminalState);
        stateMap.put(OrderStatus.CANCELLED_BY_RESTAURANT, terminalState);
        stateMap.put(OrderStatus.DELIVERY_FAILED, terminalState);
        stateMap.put(OrderStatus.PARTIALLY_REFUNDED, terminalState);
        stateMap.put(OrderStatus.CANCELLED_AND_REFUNDED, terminalState);
    }

    public static OrderState getState(OrderStatus status) {
        OrderState state = stateMap.get(status);
        if (state == null) {
            throw new IllegalArgumentException("Unknown state for status: " + status);
        }
        return state;
    }
}
