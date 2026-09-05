package com.fooddelivery.order.ledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.ledger.LedgerAccountResolver;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.order.enums.InitiatorType;

import java.math.BigDecimal;
import java.util.UUID;

public class LedgerBookkeeperTest {

    private LedgerBookkeeper bookkeeper;
    private OutboxEventRepository outboxRepo;
    private ObjectMapper objectMapper;
    private LedgerAccountResolver accountResolver;

    @BeforeEach
    void setUp() {
        outboxRepo = mock(OutboxEventRepository.class);
        objectMapper = new ObjectMapper();
        accountResolver = mock(LedgerAccountResolver.class);
        bookkeeper = new LedgerBookkeeper(outboxRepo, objectMapper, accountResolver);
    }

    @Test
    void testBookPaymentCaptured() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("100.00"));

        bookkeeper.bookPaymentCaptured(order, "CREDIT_CARD");

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepo, times(1)).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertEquals("LEDGER", saved.getAggregateType().name());
        assertTrue(saved.getPayload().contains("CREDIT_CARD"));
    }

    @Test
    void testBookCashCollected() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setDeliveryExecutiveId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("50.00"));

        bookkeeper.bookCashCollected(order);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepo, times(1)).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertEquals("LEDGER", saved.getAggregateType().name());
        assertTrue(saved.getPayload().contains("CASH_COLLECTED"));
    }

    @Test
    void testBookRefund_OriginalMethod() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        
        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setDestination(RefundDestination.ORIGINAL_METHOD);
        refund.setAmount(new BigDecimal("20.00"));
        refund.setInitiatedByType(InitiatorType.CUSTOMER);

        bookkeeper.bookRefund(order, refund);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepo, times(1)).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertTrue(saved.getPayload().contains("REFUND"));
    }
}
