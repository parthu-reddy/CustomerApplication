package com.fooddelivery.order.service.state;

import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.service.state.impl.*;

import java.util.EnumMap;
import java.util.Map;

public class OrderStateFactory {

    private static final Map<OrderStatus, OrderState> stateMap = new EnumMap<>(OrderStatus.class);

    static {
        stateMap.put(OrderStatus.CREATED, new CreatedState());
        stateMap.put(OrderStatus.PENDING_ACCEPTANCE, new PendingAcceptanceState());
        stateMap.put(OrderStatus.AWAITING_DELAY_APPROVAL, new AwaitingDelayApprovalState());
        stateMap.put(OrderStatus.ACCEPTED, new AcceptedState());
        stateMap.put(OrderStatus.PREPARING, new PreparingState());
        stateMap.put(OrderStatus.READY_FOR_PICKUP, new ReadyForPickupState());
        stateMap.put(OrderStatus.PICKED_UP, new PickedUpState());
        
        // Terminal states
        TerminalState terminalState = new TerminalState();
        stateMap.put(OrderStatus.DELIVERED, terminalState);
        stateMap.put(OrderStatus.CANCELLED, terminalState);
        stateMap.put(OrderStatus.CANCELLED_BY_RESTAURANT, terminalState);
        stateMap.put(OrderStatus.DELIVERY_FAILED, terminalState);
    }

    public static OrderState getState(OrderStatus status) {
        OrderState state = stateMap.get(status);
        if (state == null) {
            throw new IllegalArgumentException("Unknown state for status: " + status);
        }
        return state;
    }
}
