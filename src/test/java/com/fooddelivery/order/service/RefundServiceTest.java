package com.fooddelivery.order.service;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.repository.RefundRepository;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.common.enums.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.client.WalletInternalClient;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private RefundRepository refundRepository;
    @Mock private IOrderRepository orderRepository;
    @Mock private IPaymentIntentRepository paymentIntentRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private LedgerBookkeeper ledgerBookkeeper;
    // A real mapper: a mocked writeValueAsString returns null, so no payload could be asserted on.
    @org.mockito.Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private WalletInternalClient walletClient;
    
    @InjectMocks
    private RefundService refundService;

    @Test
    void complete_shouldProcessRefundSuccessfully() {
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund();
        refund.setId(refundId);
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setPaymentIntentId(UUID.randomUUID());
        
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalAmount(java.math.BigDecimal.valueOf(100));
        order.setCustomerId(UUID.randomUUID());
        refund.setOrderId(order.getId());
        refund.setAmount(java.math.BigDecimal.valueOf(50));
        
        PaymentIntent intent = new PaymentIntent();
        intent.setId(refund.getPaymentIntentId());
        intent.setAmount(java.math.BigDecimal.valueOf(100));
        
        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentIntentRepository.findById(refund.getPaymentIntentId())).thenReturn(Optional.of(intent));
        when(refundRepository.sumByOrderAndStatusIn(any(), any())).thenReturn(java.math.BigDecimal.ZERO);
        
        refundService.complete(refundId, "PAY-REF-123");
        
        verify(ledgerBookkeeper).bookRefund(eq(order), eq(refund), any());
    }

    /**
     * Destination routing, the remaining-amount guard and the over-completion guard live in
     * RefundDestinationMatrixTest, RefundRemainingTest and RefundOverCompletionTest. What is left
     * here is the lifecycle either side of them.
     */
    @Test
    void fail_recordsTheReasonAndStopsThere() {
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund();
        refund.setId(refundId);
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setOrderId(UUID.randomUUID());
        refund.setAmount(java.math.BigDecimal.valueOf(50));

        Order order = new Order();
        order.setId(refund.getOrderId());
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(java.math.BigDecimal.valueOf(100));

        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));
        when(orderRepository.findById(refund.getOrderId())).thenReturn(Optional.of(order));

        refundService.fail(refundId, "gateway declined the refund");

        org.junit.jupiter.api.Assertions.assertEquals(RefundStatus.FAILED, refund.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("gateway declined the refund", refund.getFailureReason());
        // A failed refund books nothing: no money moved.
        verify(ledgerBookkeeper, never()).bookRefund(any(), any(), any());
    }

    @Test
    void fail_isIdempotent() {
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund();
        refund.setId(refundId);
        refund.setStatus(RefundStatus.FAILED);
        refund.setFailureReason("the original reason");
        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));

        refundService.fail(refundId, "a different reason");

        org.junit.jupiter.api.Assertions.assertEquals("the original reason", refund.getFailureReason());
        verify(orderRepository, never()).findById(any());
    }

    /** A stuck refund is re-enqueued, and its attempt count is what eventually stops it. */
    @Test
    void retryStuck_reEnqueuesAndCountsTheAttempt() throws Exception {
        Refund stuck = new Refund();
        stuck.setId(UUID.randomUUID());
        stuck.setOrderId(UUID.randomUUID());
        stuck.setStatus(RefundStatus.PROCESSING);
        stuck.setAmount(java.math.BigDecimal.valueOf(50));
        stuck.setAttempts(1);
        stuck.setPaymentIntentId(UUID.randomUUID());

        PaymentIntent intent = new PaymentIntent();
        intent.setId(stuck.getPaymentIntentId());
        intent.setGatewayOrderId("gw_1");
        intent.setGatewayName(com.fooddelivery.common.enums.PaymentGateway.RAZORPAY);

        when(refundRepository.findStuckProcessing(any())).thenReturn(java.util.List.of(stuck));
        when(paymentIntentRepository.findById(stuck.getPaymentIntentId())).thenReturn(Optional.of(intent));

        refundService.retryStuck();

        org.junit.jupiter.api.Assertions.assertEquals(2, stuck.getAttempts());

        // The retry must carry the SAME refund id. A new id per attempt makes the gateway create a
        // second refund, so a customer is paid twice for one request. Phase 4's break-test asked for
        // this assertion by name; the sweeper test it pointed at has no such case.
        org.mockito.ArgumentCaptor<com.fooddelivery.common.outbox.entity.OutboxEventEntity> saved =
                org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.outbox.entity.OutboxEventEntity.class);
        verify(outboxEventRepository).save(saved.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                saved.getValue().getPayload().contains(stuck.getId().toString()),
                "the retry payload must name the original refund id, was: " + saved.getValue().getPayload());
    }

    /** After three attempts the refund is failed rather than retried forever. */
    @Test
    void retryStuck_givesUpAfterThreeAttempts() throws Exception {
        Refund stuck = new Refund();
        stuck.setId(UUID.randomUUID());
        stuck.setOrderId(UUID.randomUUID());
        stuck.setStatus(RefundStatus.PROCESSING);
        stuck.setAmount(java.math.BigDecimal.valueOf(50));
        stuck.setAttempts(3);

        Order order = new Order();
        order.setId(stuck.getOrderId());
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(java.math.BigDecimal.valueOf(100));

        when(refundRepository.findStuckProcessing(any())).thenReturn(java.util.List.of(stuck));
        when(refundRepository.findById(stuck.getId())).thenReturn(Optional.of(stuck));
        when(orderRepository.findById(stuck.getOrderId())).thenReturn(Optional.of(order));

        refundService.retryStuck();

        org.junit.jupiter.api.Assertions.assertEquals(RefundStatus.FAILED, stuck.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("Max retries exceeded", stuck.getFailureReason());

        // fail() does write an outbox row -- the customer's REFUND_FAILED notification. What must
        // not be written is another attempt at the gateway.
        org.mockito.ArgumentCaptor<com.fooddelivery.common.outbox.entity.OutboxEventEntity> saved =
                org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.outbox.entity.OutboxEventEntity.class);
        verify(outboxEventRepository, org.mockito.Mockito.atLeast(0)).save(saved.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                saved.getAllValues().stream().noneMatch(e ->
                        e.getEventType() == com.fooddelivery.common.constants.EventType.PAYMENT_REFUND_REQUESTED),
                "a refund that has run out of attempts must not be re-enqueued at the gateway");
    }

    /** Completing an already-completed refund must not book the ledger a second time. */
    @Test
    void complete_isIdempotent() {
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund();
        refund.setId(refundId);
        refund.setStatus(RefundStatus.COMPLETED);
        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));

        refundService.complete(refundId, "PAY-REF-123");

        verify(ledgerBookkeeper, never()).bookRefund(any(), any(), any());
    }
}
