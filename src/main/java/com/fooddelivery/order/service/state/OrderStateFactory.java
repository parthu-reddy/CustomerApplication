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
        stateMap.put(OrderStatus.HANDED_OVER, new HandedOverState());
        
        // Terminal states. Driven off isTerminal() rather than a hand-listed set, so a new terminal
        // status cannot arrive without a state and blow up OrderStateFactory.getState at runtime.
        TerminalState terminalState = new TerminalState();
        for (OrderStatus status : OrderStatus.values()) {
            if (status.isTerminal()) {
                stateMap.put(status, terminalState);
            }
        }
    }

    public static OrderState getState(OrderStatus status) {
        OrderState state = stateMap.get(status);
        if (state == null) {
            throw new IllegalArgumentException("Unknown state for status: " + status);
        }
        return state;
    }
}
