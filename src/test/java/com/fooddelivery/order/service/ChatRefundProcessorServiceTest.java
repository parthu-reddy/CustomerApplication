package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.event.OutboxEvent;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.entity.SupportTicket;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.SupportTicketRepository;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatRefundProcessorServiceTest {

    @Mock
    private IOrderRepository orderRepository;
    @Mock
    private SupportTicketRepository supportTicketRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private IIdempotencyKeyRepository idempotencyKeyRepository;

    private ObjectMapper objectMapper;

    @InjectMocks
    private ChatRefundProcessorService chatRefundProcessorService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        chatRefundProcessorService = new ChatRefundProcessorService(
                orderRepository, supportTicketRepository, outboxEventRepository, idempotencyKeyRepository, objectMapper, transactionTemplate
        );
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void testHandleQuoteRequest_FullRefund() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\", \"refundType\":\"FULL\"}";
        OutboxEvent event = new OutboxEvent();
        event.setType("CHAT_REFUND_QUOTE_REQUESTED");
        event.setPayload(payload);
        event.setAggregateId(UUID.randomUUID().toString());

        Order order = new Order();
        order.setId(orderId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(com.fooddelivery.common.enums.OrderStatus.CREATED);
        
        when(idempotencyKeyRepository.existsById("chat_event:" + event.getId())).thenReturn(false);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        chatRefundProcessorService.handleChatEvents(event);

        verify(outboxEventRepository, times(1)).save(any());
    }

    @Test
    void testHandleRefundRequest_PartialRefund() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\", \"customerId\":\"" + customerId + "\", \"refundType\":\"PARTIAL\", \"items\":[{\"itemId\":\"" + itemId + "\", \"quantity\":1}]}";
        OutboxEvent event = new OutboxEvent();
        event.setType("CHAT_REFUND_REQUESTED");
        event.setPayload(payload);
        event.setAggregateId(UUID.randomUUID().toString());

        when(transactionTemplate.execute(any())).thenReturn(false);

        Order order = new Order();
        order.setId(orderId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(com.fooddelivery.common.enums.OrderStatus.HANDED_OVER);
        
        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("50.00"));
        order.setOrderItems(java.util.Set.of(item));

        lenient().when(idempotencyKeyRepository.existsById("chat_event:" + event.getId())).thenReturn(false);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        chatRefundProcessorService.handleChatEvents(event);

        verify(supportTicketRepository, times(1)).save(any());
        verify(outboxEventRepository, times(1)).save(any());
    }

    @Test
    void testHandleRefundRequest_PartialRefund_ExceedsRefundedQuantity() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\", \"customerId\":\"" + customerId + "\", \"refundType\":\"PARTIAL\", \"items\":[{\"itemId\":\"" + itemId + "\", \"quantity\":1}]}";
        OutboxEvent event = new OutboxEvent();
        event.setType("CHAT_REFUND_REQUESTED");
        event.setPayload(payload);
        event.setAggregateId(UUID.randomUUID().toString());

        when(transactionTemplate.execute(any())).thenReturn(false);

        Order order = new Order();
        order.setId(orderId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(com.fooddelivery.common.enums.OrderStatus.HANDED_OVER);
        
        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setQuantity(2);
        item.setRefundedQuantity(2); // Already fully refunded
        item.setPrice(new BigDecimal("50.00"));
        order.setOrderItems(java.util.Set.of(item));

        lenient().when(idempotencyKeyRepository.existsById("chat_event:" + event.getId())).thenReturn(false);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        chatRefundProcessorService.handleChatEvents(event);

        // It should publish an error event because of the IllegalArgumentException
        verify(outboxEventRepository, times(1)).save(any());
        // Verify no ticket was created
        verify(supportTicketRepository, never()).save(any());
    }
}
