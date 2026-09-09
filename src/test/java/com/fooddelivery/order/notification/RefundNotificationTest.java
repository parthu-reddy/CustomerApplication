package com.fooddelivery.order.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AggregateType;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.RefundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefundNotificationTest {

    @Mock
    private RefundRepository refundRepository;
    @Mock
    private IOrderRepository orderRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private com.fooddelivery.order.repository.IPaymentIntentRepository paymentIntentRepository;
    @Mock
    private LedgerBookkeeper ledgerBookkeeper;
    // A real mapper, not a mock: a mocked writeValueAsString returns null, so the payload these
    // tests are about was never actually produced and nothing could assert on it.
    @org.mockito.Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RefundService refundService;

    @Test
    public void testNotificationOnFail() throws Exception {
        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        
        Refund refund = new Refund();
        refund.setId(refundId);
        refund.setOrderId(orderId);
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setAmount(new BigDecimal("10.00"));

        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(UUID.randomUUID());

        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        refundService.fail(refundId, "Error");

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertEquals(AggregateType.NOTIFICATION, saved.getAggregateType());
        assertEquals(EventType.NOTIFICATION_REQUEST, saved.getEventType());

        // Deserialised, not string-matched. `contains("REFUND_FAILED")` passed just as happily on a
        // free-form Map.of("template", "REFUND_FAILED", ...) payload, which the notification service
        // cannot read -- the customer would simply never be told the refund failed. Found 2026-09-09
        // performing Phase 4's break-test 3.
        com.fooddelivery.common.event.NotificationRequestEvent event =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(saved.getPayload(),
                                com.fooddelivery.common.event.NotificationRequestEvent.class);
        assertEquals("REFUND_FAILED", event.getEventName());
        assertEquals(order.getCustomerId(), event.getUserId());
        assertTrue(event.getTemplateParams().contains(orderId.toString()),
                "the message must name the order, was: " + event.getTemplateParams());
        assertTrue(event.getTemplateParams().contains("10.00"),
                "the message must name the amount, was: " + event.getTemplateParams());
    }

    /**
     * A full refund and a partial refund are different messages to the customer. Sending
     * PAYMENT_REFUNDED for a partial one tells them their whole order was refunded.
     */
    @Test
    public void testNotificationOnCompletion_DistinguishesPartialFromFull() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();

        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("100.00"));

        com.fooddelivery.order.entity.PaymentIntent intent = new com.fooddelivery.order.entity.PaymentIntent();
        intent.setId(intentId);
        intent.setAmount(new BigDecimal("100.00"));

        Refund partial = new Refund();
        partial.setId(UUID.randomUUID());
        partial.setOrderId(orderId);
        partial.setPaymentIntentId(intentId);
        partial.setStatus(RefundStatus.PROCESSING);
        partial.setAmount(new BigDecimal("40.00"));

        when(refundRepository.findById(partial.getId())).thenReturn(Optional.of(partial));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentIntentRepository.findById(intentId)).thenReturn(Optional.of(intent));
        when(refundRepository.sumByOrderAndStatusIn(any(), any())).thenReturn(BigDecimal.ZERO);

        refundService.complete(partial.getId(), "PAY-REF-1");

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("PAYMENT_PARTIALLY_REFUNDED"),
                "a 40.00 refund on a 100.00 order is partial: " + captor.getValue().getPayload());
    }

    @Test
    public void testNotificationOnCompletion_FullRefundSaysSo() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();

        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("100.00"));

        com.fooddelivery.order.entity.PaymentIntent intent = new com.fooddelivery.order.entity.PaymentIntent();
        intent.setId(intentId);
        intent.setAmount(new BigDecimal("100.00"));

        Refund full = new Refund();
        full.setId(UUID.randomUUID());
        full.setOrderId(orderId);
        full.setPaymentIntentId(intentId);
        full.setStatus(RefundStatus.PROCESSING);
        full.setAmount(new BigDecimal("100.00"));

        when(refundRepository.findById(full.getId())).thenReturn(Optional.of(full));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentIntentRepository.findById(intentId)).thenReturn(Optional.of(intent));
        when(refundRepository.sumByOrderAndStatusIn(any(), any())).thenReturn(BigDecimal.ZERO);

        refundService.complete(full.getId(), "PAY-REF-2");

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        String payload = captor.getValue().getPayload();
        assertTrue(payload.contains("PAYMENT_REFUNDED") && !payload.contains("PARTIALLY"), payload);
    }
}
