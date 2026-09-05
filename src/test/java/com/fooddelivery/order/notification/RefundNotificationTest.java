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
    private LedgerBookkeeper ledgerBookkeeper;
    @Mock
    private ObjectMapper objectMapper;

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
    }
}
