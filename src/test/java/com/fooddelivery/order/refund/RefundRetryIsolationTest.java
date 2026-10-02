package com.fooddelivery.order.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.*;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.*;
import com.fooddelivery.order.repository.*;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.service.AdminDlqService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** One owned refund fixture per invocation; no aging sleep or provider invocation. */
class RefundRetryIsolationTest {
    final RefundRepository refunds = mock(RefundRepository.class);
    final IPaymentIntentRepository intents = mock(IPaymentIntentRepository.class);
    final IOrderRepository orders = mock(IOrderRepository.class);
    final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    final ObjectMapper mapper = new ObjectMapper();
    final RefundService service = new RefundService(refunds, orders, intents, outbox,
            mock(LedgerBookkeeper.class), mapper, mock(RefundItemRepository.class));
    final Order order = new Order();
    final PaymentIntent intent = new PaymentIntent();
    final Refund refund = new Refund();

    void arrange() {
        order.setId(UUID.randomUUID()); order.setCustomerId(UUID.randomUUID());
        intent.setId(UUID.randomUUID()); intent.setInternalOrderId(order.getId());
        intent.setAmount(new BigDecimal("43.35")); intent.setStatus(PaymentIntentStatus.SUCCESS);
        intent.setPaymentMethod(PaymentMethod.CARD); intent.setGatewayName(PaymentGateway.RAZORPAY);
        intent.setGatewayOrderId("mock_rzp_txn_"+order.getId());
        refund.setId(UUID.randomUUID()); refund.setOrderId(order.getId()); refund.setPaymentIntentId(intent.getId());
        refund.setAmount(new BigDecimal("43.35")); refund.setStatus(RefundStatus.FAILED);
        refund.setDestination(RefundDestination.ORIGINAL_METHOD); refund.setAttempts(4);
        refund.setFailureReason("Previous callback failed");
        when(refunds.findByIdForUpdate(refund.getId())).thenReturn(Optional.of(refund));
        when(intents.findByInternalOrderIdForUpdate(order.getId())).thenReturn(Optional.of(intent));
        when(refunds.sumByOrderAndStatusIn(eq(order.getId()), any())).thenReturn(BigDecimal.ZERO);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=RefundDestination.class, names={"ORIGINAL_METHOD","STORE_CREDIT"})
    void selectedRefundKeepsIdentityDestinationAndAttemptHistory(RefundDestination destination) throws Exception {
        arrange(); refund.setDestination(destination);
        service.retryFailed(refund.getId());
        var saved = org.mockito.ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outbox).save(saved.capture());
        var event = saved.getValue(); var payload = mapper.readTree(event.getPayload());
        assertEquals(refund.getId().toString(), payload.path("refundId").asText());
        assertEquals(order.getId().toString(), payload.path("orderId").asText());
        assertEquals(0, refund.getAmount().compareTo(new BigDecimal(payload.path("amount").asText())));
        assertEquals(destination==RefundDestination.STORE_CREDIT ? EventType.WALLET_CREDIT_REQUESTED : EventType.PAYMENT_REFUND_REQUESTED, event.getEventType());
        assertEquals("refund_retry:"+refund.getId()+":5", event.getIdempotencyKey());
        assertEquals(RefundStatus.PROCESSING, refund.getStatus()); assertEquals(5, refund.getAttempts());
        assertNull(refund.getFailureReason()); assertNull(refund.getCompletedAt());
        verify(refunds, never()).findStuckProcessing(any()); verify(refunds, never()).findById(any());
        verify(refunds).save(refund);
    }

    @Test void selectedFailedAmountCannotConsumeBalanceAlreadyReservedElsewhere() {
        arrange(); when(refunds.sumByOrderAndStatusIn(eq(order.getId()), any())).thenReturn(new BigDecimal("0.01"));
        assertEquals("REFUND_EXCEEDS_REMAINING", assertThrows(IllegalStateException.class,
                () -> service.retryFailed(refund.getId())).getMessage());
        assertEquals(RefundStatus.FAILED, refund.getStatus()); assertEquals(4, refund.getAttempts());
        verifyNoInteractions(outbox); verify(refunds,never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=RefundStatus.class, names={"REQUESTED","PROCESSING","COMPLETED","CANCELLED"})
    void duplicateOrTerminalSelectionDoesNotQueueAnotherAction(RefundStatus status) {
        arrange(); refund.setStatus(status);
        assertThrows(IllegalStateException.class, () -> service.retryFailed(refund.getId()));
        verifyNoInteractions(outbox); verify(intents,never()).findByInternalOrderIdForUpdate(any());
        assertEquals(4, refund.getAttempts());
    }

    @Test void enqueueFailureDoesNotTurnFailedRefundIntoProcessing() {
        arrange(); when(outbox.save(any())).thenThrow(new IllegalStateException("outbox unavailable"));
        assertThrows(IllegalStateException.class, () -> service.retryFailed(refund.getId()));
        assertEquals(RefundStatus.FAILED,refund.getStatus()); assertEquals(4,refund.getAttempts());
        assertEquals("Previous callback failed",refund.getFailureReason());
        verify(refunds,never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=PaymentIntentStatus.class, names={"INITIATED","FAILED","REFUNDED"})
    void paymentWithoutAvailableConfirmedBalanceCannotBeRetried(PaymentIntentStatus status) {
        arrange(); intent.setStatus(status);
        assertEquals("REFUND_STATE_INVALID", assertThrows(IllegalStateException.class,
                () -> service.retryFailed(refund.getId())).getMessage());
        verifyNoInteractions(outbox); assertEquals(RefundStatus.FAILED,refund.getStatus());
    }

    @Test void noChargeDestinationCannotProduceAProviderRetry() {
        arrange();refund.setDestination(RefundDestination.NONE);
        assertEquals("REFUND_DESTINATION_NOT_RETRYABLE",assertThrows(IllegalStateException.class,
                () -> service.retryFailed(refund.getId())).getMessage());
        verifyNoInteractions(outbox);
    }

    @Test void incompleteGatewayIdentityCannotBeQueued() {
        arrange();intent.setGatewayOrderId(" ");
        assertEquals("REFUND_GATEWAY_REQUIRED",assertThrows(IllegalStateException.class,
                () -> service.retryFailed(refund.getId())).getMessage());
        verifyNoInteractions(outbox);
    }

    @Test void wrongIntentIsRejectedWithoutEnqueue() {
        arrange(); intent.setId(UUID.randomUUID());
        assertEquals("REFUND_PAYMENT_INTENT_MISMATCH",assertThrows(IllegalStateException.class,
                () -> service.retryFailed(refund.getId())).getMessage());
        verifyNoInteractions(outbox);
    }

    @Test void adminDelegatesToExactRetryWithoutGlobalSweepOrRepositoryMutation() {
        var selectedService=mock(RefundService.class); UUID id=UUID.randomUUID();
        var admin=new AdminDlqService(mock(org.springframework.kafka.core.ConsumerFactory.class),
                mock(org.springframework.kafka.core.KafkaTemplate.class),refunds,selectedService);
        admin.retryRefund(id);
        verify(selectedService).retryFailed(id); verify(selectedService,never()).retryStuck();
        verifyNoInteractions(refunds);
    }
}
