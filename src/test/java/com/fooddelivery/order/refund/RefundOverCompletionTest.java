package com.fooddelivery.order.refund;

import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.repository.RefundRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RefundOverCompletionTest {

    @Test
    void testCompletionRefusesOverRefund() {
        RefundRepository refundRepo = Mockito.mock(RefundRepository.class);
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        IPaymentIntentRepository intentRepo = Mockito.mock(IPaymentIntentRepository.class);

        RefundService service = new RefundService(refundRepo, orderRepo, intentRepo, null, null, null, null);

        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        
        Refund refund = new Refund();
        refund.setId(refundId);
        refund.setOrderId(orderId);
        refund.setAmount(new BigDecimal("50.00"));
        refund.setStatus(RefundStatus.PROCESSING);
        UUID intentId = UUID.randomUUID();
        refund.setPaymentIntentId(intentId);
        
        Order order = new Order();
        order.setId(orderId);
        
        PaymentIntent intent = new PaymentIntent();
        intent.setAmount(new BigDecimal("100.00"));
        
        when(refundRepo.findById(refundId)).thenReturn(Optional.of(refund));
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(intentRepo.findById(intentId)).thenReturn(Optional.of(intent));
        
        // Setup over refund: already completed = 70, current refund = 50. Total = 120 > 100
        when(refundRepo.sumByOrderAndStatusIn(orderId, java.util.List.of(RefundStatus.COMPLETED)))
                .thenReturn(new BigDecimal("70.00"));
        
        service.complete(refundId, "TXN123");
        
        assertEquals(RefundStatus.FAILED, refund.getStatus());
        assertEquals("OVER_REFUND_BLOCKED", refund.getFailureReason());
        Mockito.verify(refundRepo).save(refund);
    }

    /** A completion that stays within the intent must still go through. */
    @Test
    void testCompletionWithinTheIntentSucceeds() {
        RefundRepository refundRepo = Mockito.mock(RefundRepository.class);
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        IPaymentIntentRepository intentRepo = Mockito.mock(IPaymentIntentRepository.class);
        com.fooddelivery.order.ledger.LedgerBookkeeper bookkeeper =
                Mockito.mock(com.fooddelivery.order.ledger.LedgerBookkeeper.class);
        RefundService service = new RefundService(refundRepo, orderRepo, intentRepo,
                Mockito.mock(com.fooddelivery.common.outbox.repository.OutboxEventRepository.class),
                bookkeeper, new com.fasterxml.jackson.databind.ObjectMapper(), null);

        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();

        Refund refund = new Refund();
        refund.setId(refundId);
        refund.setOrderId(orderId);
        refund.setAmount(new BigDecimal("30.00"));
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setPaymentIntentId(intentId);

        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("100.00"));

        PaymentIntent intent = new PaymentIntent();
        intent.setAmount(new BigDecimal("100.00"));
        intent.setGatewayName(com.fooddelivery.common.enums.PaymentGateway.RAZORPAY);

        when(refundRepo.findById(refundId)).thenReturn(Optional.of(refund));
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(intentRepo.findById(intentId)).thenReturn(Optional.of(intent));
        when(refundRepo.sumByOrderAndStatusIn(orderId, java.util.List.of(RefundStatus.COMPLETED)))
                .thenReturn(new BigDecimal("60.00"));

        service.complete(refundId, "TXN123");

        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        assertEquals("TXN123", refund.getGatewayRefundId());
        // Booked against the intent's gateway, not against the payment method.
        Mockito.verify(bookkeeper).bookRefund(order, refund, "RAZORPAY");
    }

    /** Completing a refund twice must not book the ledger twice. */
    @Test
    void testCompletionIsIdempotent() {
        RefundRepository refundRepo = Mockito.mock(RefundRepository.class);
        com.fooddelivery.order.ledger.LedgerBookkeeper bookkeeper =
                Mockito.mock(com.fooddelivery.order.ledger.LedgerBookkeeper.class);
        RefundService service = new RefundService(refundRepo, Mockito.mock(IOrderRepository.class),
                Mockito.mock(IPaymentIntentRepository.class), null, bookkeeper, null, null);

        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund();
        refund.setId(refundId);
        refund.setStatus(RefundStatus.COMPLETED);
        when(refundRepo.findById(refundId)).thenReturn(Optional.of(refund));

        service.complete(refundId, "TXN123");

        Mockito.verifyNoInteractions(bookkeeper);
    }
}
