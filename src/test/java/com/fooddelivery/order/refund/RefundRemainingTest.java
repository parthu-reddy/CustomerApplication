package com.fooddelivery.order.refund;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.repository.RefundRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RefundRemainingTest {

    @Test
    void testRemainingIncludesInflight() {
        RefundRepository refundRepo = Mockito.mock(RefundRepository.class);
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        IPaymentIntentRepository intentRepo = Mockito.mock(IPaymentIntentRepository.class);

        RefundService service = new RefundService(refundRepo, orderRepo, intentRepo, null, null, null, null, null);

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        
        PaymentIntent intent = new PaymentIntent();
        intent.setAmount(new BigDecimal("100.00"));
        
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(intentRepo.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));
        
        // Sum includes in-flight refunds (REQUESTED, PROCESSING, COMPLETED)
        when(refundRepo.sumByOrderAndStatusIn(eq(orderId), any(List.class)))
                .thenReturn(new BigDecimal("60.00"));
        
        RefundCommand command = new RefundCommand();
        command.setOrderId(orderId);
        command.setAmount(new BigDecimal("50.00")); // 60 + 50 = 110 > 100
        
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.request(command));
        assertEquals("REFUND_EXCEEDS_REMAINING", ex.getMessage());
    }

    /**
     * The predicate is what matters, not the isolation. Counting only COMPLETED refunds let two
     * admin actions -- or a retry racing a webhook -- each pass the check and together exceed the
     * order total.
     */
    @Test
    void remainingIsMeasuredAgainstRequestedProcessingAndCompleted() {
        RefundRepository refundRepo = Mockito.mock(RefundRepository.class);
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        IPaymentIntentRepository intentRepo = Mockito.mock(IPaymentIntentRepository.class);
        RefundService service = new RefundService(refundRepo, orderRepo, intentRepo, null, null, null, null, null);

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        PaymentIntent intent = new PaymentIntent();
        intent.setAmount(new BigDecimal("100.00"));
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(intentRepo.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));
        when(refundRepo.sumByOrderAndStatusIn(eq(orderId), any(List.class))).thenReturn(new BigDecimal("60.00"));

        RefundCommand command = new RefundCommand();
        command.setOrderId(orderId);
        command.setAmount(new BigDecimal("50.00"));
        assertThrows(IllegalStateException.class, () -> service.request(command));

        ArgumentCaptor<List<RefundStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(refundRepo).sumByOrderAndStatusIn(eq(orderId), statuses.capture());
        assertTrue(statuses.getValue().containsAll(
                List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING, RefundStatus.COMPLETED)),
                "money already in flight must count against what is left to refund");
        assertFalse(statuses.getValue().contains(RefundStatus.FAILED),
                "a failed refund releases its reservation");
        assertFalse(statuses.getValue().contains(RefundStatus.CANCELLED));
    }

    /** Exactly the remaining amount is allowed through; only more than it is refused. */
    @Test
    void aRequestForExactlyWhatIsLeftIsAllowed() {
        RefundRepository refundRepo = Mockito.mock(RefundRepository.class);
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        IPaymentIntentRepository intentRepo = Mockito.mock(IPaymentIntentRepository.class);
        RefundService service = new RefundService(refundRepo, orderRepo, intentRepo, null, null, null, null, null);

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        PaymentIntent intent = new PaymentIntent();
        intent.setAmount(new BigDecimal("100.00"));
        intent.setPaymentMethod(com.fooddelivery.common.enums.PaymentMethod.COD);
        intent.setStatus(com.fooddelivery.common.constants.PaymentIntentStatus.INITIATED);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(intentRepo.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));
        when(refundRepo.sumByOrderAndStatusIn(eq(orderId), any(List.class))).thenReturn(new BigDecimal("60.00"));

        RefundCommand command = new RefundCommand();
        command.setOrderId(orderId);
        command.setAmount(new BigDecimal("40.00"));

        // It clears the remaining-amount gate and is stopped later, by the routing matrix.
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.request(command));
        assertEquals("REFUND_STATE_INVALID", ex.getMessage(),
                "40.00 of a remaining 40.00 must pass the remaining check");
    }
}
