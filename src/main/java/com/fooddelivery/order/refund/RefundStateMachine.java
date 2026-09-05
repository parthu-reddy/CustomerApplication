package com.fooddelivery.order.refund;

import com.fooddelivery.common.enums.RefundStatus;

public class RefundStateMachine {

    public static boolean canTransition(RefundStatus current, RefundStatus next) {
        if (current == next) return false;
        
        return switch (current) {
            case REQUESTED -> next == RefundStatus.PROCESSING || next == RefundStatus.COMPLETED || next == RefundStatus.FAILED || next == RefundStatus.CANCELLED;
            case PROCESSING -> next == RefundStatus.COMPLETED || next == RefundStatus.FAILED;
            case COMPLETED -> false;
            case FAILED -> next == RefundStatus.PROCESSING || next == RefundStatus.CANCELLED;
            case CANCELLED -> false;
            default -> false;
        };
    }
    
    public static void validateTransition(RefundStatus current, RefundStatus next) {
        if (!canTransition(current, next)) {
            throw new IllegalStateException("Invalid transition from " + current + " to " + next);
        }
    }
}
