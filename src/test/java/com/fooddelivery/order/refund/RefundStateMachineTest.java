package com.fooddelivery.order.refund;

import com.fooddelivery.common.enums.RefundStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefundStateMachineTest {

    @Test
    void canTransition_fromRequestedToProcessing_shouldReturnTrue() {
        assertTrue(RefundStateMachine.canTransition(RefundStatus.REQUESTED, RefundStatus.PROCESSING));
    }

    @Test
    void canTransition_fromProcessingToCompleted_shouldReturnTrue() {
        assertTrue(RefundStateMachine.canTransition(RefundStatus.PROCESSING, RefundStatus.COMPLETED));
    }

    @Test
    void validateTransition_invalidState_shouldThrowException() {
        assertThrows(IllegalStateException.class, () -> 
            RefundStateMachine.validateTransition(RefundStatus.COMPLETED, RefundStatus.PROCESSING)
        );
    }
}
