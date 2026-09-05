package com.fooddelivery.order.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AggregateType;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.order.enums.InitiatorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LedgerBookkeeperRefundTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private LedgerAccountResolver accountResolver;

    @InjectMocks
    private LedgerBookkeeper bookkeeper;

    @Test
    public void testRefund() throws Exception {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setPaymentMethod(PaymentMethod.CARD);
        order.setTotalAmount(new BigDecimal("100.00"));

        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setAmount(new BigDecimal("100.00"));
        refund.setDestination(RefundDestination.ORIGINAL_METHOD);
        refund.setInitiatedByType(InitiatorType.CUSTOMER);
        refund.setFaultType(FaultType.RESTAURANT_FAULT);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        bookkeeper.bookRefund(order, refund);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertEquals(AggregateType.LEDGER, saved.getAggregateType());
        assertEquals(EventType.LEDGER_TRANSACTION_REQUEST, saved.getEventType());
    }
}
