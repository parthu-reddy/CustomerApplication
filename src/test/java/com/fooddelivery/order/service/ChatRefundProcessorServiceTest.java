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

    @Mock
    private com.fooddelivery.order.refund.RefundService refundService;

    private ObjectMapper objectMapper;

    @InjectMocks
    private ChatRefundProcessorService chatRefundProcessorService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        chatRefundProcessorService = new ChatRefundProcessorService(
                orderRepository, supportTicketRepository, outboxEventRepository, idempotencyKeyRepository,
                objectMapper, transactionTemplate, refundService, null,
                // A real binder, not a mock: the chat payload is client-supplied, so the
                // binding and its validation are part of what these tests exercise.
                new com.fooddelivery.common.event.EventBinder(objectMapper,
                        jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator())
        );
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    /** Exercise Spring Kafka's actual argument conversion, with the outbox's raw JSON wire shape. */
    private void deliverWireRecord(OutboxEvent event) throws Exception {
        var method = ChatRefundProcessorService.class.getMethod("handleChatRecord", String.class, java.util.Map.class);
        var factory = new org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory();
        factory.afterPropertiesSet();
        var adapter = new org.springframework.kafka.listener.adapter.RecordMessagingMessageListenerAdapter<String, String>(chatRefundProcessorService, method);
        adapter.setHandlerMethod(new org.springframework.kafka.listener.adapter.HandlerAdapter(
                factory.createInvocableHandlerMethod(chatRefundProcessorService, method)));
        var record = new org.apache.kafka.clients.consumer.ConsumerRecord<String, String>("chat-events", 0, 0, event.getAggregateId(), event.getPayload());
        record.headers().add("eventType", event.getType().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        record.headers().add("eventId", event.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        adapter.onMessage(record, null, null);
    }

    @Test
    void testHandleQuoteRequest_FullRefund() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\", \"refundType\":\"FULL\","
                + "\"actorId\":\"" + customerId + "\",\"actorType\":\"CUSTOMER\"}";
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setType("CHAT_REFUND_QUOTE_REQUESTED");
        event.setPayload(payload);
        event.setAggregateId(UUID.randomUUID().toString());

        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(com.fooddelivery.common.enums.OrderStatus.CREATED);
        
        when(idempotencyKeyRepository.existsById("chat_event:" + event.getId())).thenReturn(false);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(refundService.quote(any(), anyList())).thenReturn(new BigDecimal("100.00"));

        deliverWireRecord(event);

        verify(outboxEventRepository, times(1)).save(any());
    }

    @Test
    void testHandleRefundRequest_PartialRefund() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\", \"customerId\":\"" + customerId + "\", \"refundType\":\"PARTIAL\","
                + "\"actorId\":\"" + customerId + "\",\"actorType\":\"CUSTOMER\","
                + " \"items\":[{\"itemId\":\"" + itemId + "\", \"quantity\":1}]}";
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setType("CHAT_REFUND_REQUESTED");
        event.setPayload(payload);
        event.setAggregateId(UUID.randomUUID().toString());

        when(transactionTemplate.execute(any())).thenReturn(false);

        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(com.fooddelivery.common.enums.OrderStatus.HANDED_OVER);
        
        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("50.00"));
        order.setOrderItems(java.util.Set.of(item));

        lenient().when(idempotencyKeyRepository.existsById("chat_event:" + event.getId())).thenReturn(false);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(refundService.quote(any(), anyList())).thenReturn(new BigDecimal("50.00"));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        deliverWireRecord(event);

        var ticketCaptor=org.mockito.ArgumentCaptor.forClass(SupportTicket.class);
        verify(supportTicketRepository).save(ticketCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("50.00"),ticketCaptor.getValue().getRefundAmount());
        org.junit.jupiter.api.Assertions.assertEquals(SupportTicket.TicketStatus.OPEN,ticketCaptor.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(itemId.toString(),objectMapper.readTree(ticketCaptor.getValue().getRequestedRefundItems()).get(0).get("itemId").asText());
        var responseCaptor=org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.outbox.entity.OutboxEventEntity.class);
        verify(outboxEventRepository).save(responseCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("50.00"),objectMapper.readTree(responseCaptor.getValue().getPayload()).get("amount").decimalValue().setScale(2));
        verify(refundService,never()).request(any());
    }

    @Test
    void testHandleRefundRequest_PartialRefund_ExceedsRefundedQuantity() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\", \"customerId\":\"" + customerId + "\", \"refundType\":\"PARTIAL\","
                + "\"actorId\":\"" + customerId + "\",\"actorType\":\"CUSTOMER\","
                + " \"items\":[{\"itemId\":\"" + itemId + "\", \"quantity\":1}]}";
        OutboxEvent event = new OutboxEvent();
        event.setType("CHAT_REFUND_REQUESTED");
        event.setPayload(payload);
        event.setAggregateId(UUID.randomUUID().toString());

        when(transactionTemplate.execute(any())).thenReturn(false);

        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(com.fooddelivery.common.enums.OrderStatus.HANDED_OVER);
        
        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setQuantity(2);
        // item.setRefundedQuantity(2); // Already fully refunded
        item.setPrice(new BigDecimal("50.00"));
        order.setOrderItems(java.util.Set.of(item));

        lenient().when(idempotencyKeyRepository.existsById("chat_event:" + event.getId())).thenReturn(false);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(refundService.quote(any(), anyList())).thenThrow(new IllegalArgumentException("Cannot refund more items than originally purchased or already refunded"));

        chatRefundProcessorService.handleChatEvents(event);

        // It should publish an error event because of the IllegalArgumentException
        verify(outboxEventRepository, times(1)).save(any());
        // Verify no ticket was created
        verify(supportTicketRepository, never()).save(any());
    }

    /**
     * The chat payload is the message content the client sent, so binding it is the validation
     * boundary for a money path. Malformed input must reach the customer as CHAT_REFUND_ERROR, not
     * retry into the DLT -- retrying cannot make a client's JSON valid.
     *
     * <p>The order and the quote are stubbed to SUCCEED, deliberately. An earlier version of this
     * test left them unstubbed, so every bad payload produced an error event by way of
     * "Order not found" and the test passed with the constraints removed -- a pass earned by
     * reaching the error path for the wrong reason. With the happy path open, the only way to get a
     * CHAT_REFUND_ERROR is for the bind or its constraints to reject the payload.
     */
    @Test
    void malformedClientPayloadBecomesAnErrorEventRatherThanAPoisonMessage() throws Exception {
        UUID goodOrderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(goodOrderId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(com.fooddelivery.common.enums.OrderStatus.CREATED);
        lenient().when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        lenient().when(refundService.quote(any(), anyList())).thenReturn(new BigDecimal("10.00"));

        for (String badPayload : java.util.List.of(
                "{\"refundType\":\"FULL\"}",                                     // orderId missing -> @NotNull
                "{\"orderId\":\"not-a-uuid\",\"refundType\":\"FULL\"}",         // orderId not a UUID
                "{\"orderId\":\"" + goodOrderId + "\",\"refundType\":\"PARTIAL\","
                        + "\"items\":[{\"itemId\":\"not-a-uuid\",\"quantity\":1}]}",   // item id not a UUID
                "{\"orderId\":\"" + goodOrderId + "\",\"refundType\":\"PARTIAL\","
                        + "\"items\":[{\"itemId\":\"" + UUID.randomUUID() + "\",\"quantity\":0}]}")) {  // @Positive
            org.mockito.Mockito.clearInvocations(outboxEventRepository);
            OutboxEvent event = new OutboxEvent();
            event.setType("CHAT_REFUND_QUOTE_REQUESTED");
            event.setPayload(badPayload);
            event.setAggregateId(UUID.randomUUID().toString());
            lenient().when(idempotencyKeyRepository.existsById("chat_event:" + event.getId())).thenReturn(false);

            chatRefundProcessorService.handleChatEvents(event);

            org.mockito.ArgumentCaptor<com.fooddelivery.common.outbox.entity.OutboxEventEntity> captor =
                    org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.outbox.entity.OutboxEventEntity.class);
            verify(outboxEventRepository, times(1)).save(captor.capture());
            org.junit.jupiter.api.Assertions.assertEquals(
                    com.fooddelivery.common.constants.EventType.CHAT_REFUND_ERROR,
                    captor.getValue().getEventType(),
                    "expected a CHAT_REFUND_ERROR for payload: " + badPayload);
        }
    }

    /** The control for the test above: with the same stubs, a VALID payload must not error. */
    @Test
    void aValidPayloadReachesTheQuoteResponse() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(com.fooddelivery.common.enums.OrderStatus.CREATED);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(refundService.quote(any(), anyList())).thenReturn(new BigDecimal("10.00"));

        OutboxEvent event = new OutboxEvent();
        event.setType("CHAT_REFUND_QUOTE_REQUESTED");
        event.setPayload("{\"orderId\":\"" + orderId + "\",\"refundType\":\"FULL\","
                + "\"actorId\":\"" + customerId + "\",\"actorType\":\"CUSTOMER\"}");
        event.setAggregateId(UUID.randomUUID().toString());
        when(idempotencyKeyRepository.existsById("chat_event:" + event.getId())).thenReturn(false);

        chatRefundProcessorService.handleChatEvents(event);

        org.mockito.ArgumentCaptor<com.fooddelivery.common.outbox.entity.OutboxEventEntity> captor =
                org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.outbox.entity.OutboxEventEntity.class);
        verify(outboxEventRepository, times(1)).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.fooddelivery.common.constants.EventType.CHAT_REFUND_QUOTE_RESPONSE,
                captor.getValue().getEventType());
    }

    @Test
    void aValidPartialRequestStillBindsItsItems() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\",\"refundType\":\"PARTIAL\","
                + "\"actorId\":\"" + customerId + "\",\"actorType\":\"CUSTOMER\","
                + "\"items\":[{\"itemId\":\"" + itemId + "\",\"quantity\":2}]}";
        OutboxEvent event = new OutboxEvent();
        event.setType("CHAT_REFUND_QUOTE_REQUESTED");
        event.setPayload(payload);
        event.setAggregateId(UUID.randomUUID().toString());

        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(com.fooddelivery.common.enums.OrderStatus.CREATED);

        when(idempotencyKeyRepository.existsById("chat_event:" + event.getId())).thenReturn(false);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(refundService.quote(any(), anyList())).thenReturn(new BigDecimal("20.00"));

        chatRefundProcessorService.handleChatEvents(event);

        // The bound item must reach RefundService with its id and quantity intact.
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.List<com.fooddelivery.order.refund.RefundCommand.Item>> items =
                org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(refundService).quote(org.mockito.ArgumentMatchers.eq(orderId), items.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, items.getValue().size());
        org.junit.jupiter.api.Assertions.assertEquals(itemId, items.getValue().get(0).getOrderItemId());
        org.junit.jupiter.api.Assertions.assertEquals(2, items.getValue().get(0).getQuantity());
    }

    @Test
    void forgedActorCannotCreateARefundTicket() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID actualCustomerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(actualCustomerId);
        order.setTotalAmount(new BigDecimal("100.00"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OutboxEvent event = new OutboxEvent();
        event.setType("CHAT_REFUND_REQUESTED");
        event.setAggregateId(UUID.randomUUID().toString());
        event.setPayload("{\"orderId\":\"" + orderId + "\",\"customerId\":\"" + actualCustomerId + "\","
                + "\"actorId\":\"" + attackerId + "\",\"actorType\":\"CUSTOMER\"}");

        chatRefundProcessorService.handleChatEvents(event);

        verify(supportTicketRepository, never()).save(any(SupportTicket.class));
        verify(refundService, never()).quote(any(), anyList());
        org.mockito.ArgumentCaptor<com.fooddelivery.common.outbox.entity.OutboxEventEntity> error =
                org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.outbox.entity.OutboxEventEntity.class);
        verify(outboxEventRepository).save(error.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.fooddelivery.common.constants.EventType.CHAT_REFUND_ERROR,
                error.getValue().getEventType());
    }

    @Test
    void legacyRefundEventWithoutTheServerStampedActorFailsClosed() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID actualCustomerId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(actualCustomerId);
        order.setTotalAmount(new BigDecimal("100.00"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OutboxEvent event = new OutboxEvent();
        event.setType("CHAT_REFUND_REQUESTED");
        event.setAggregateId(UUID.randomUUID().toString());
        // This is the pre-remediation wire shape. customerId alone is not a trusted actor.
        event.setPayload("{\"orderId\":\"" + orderId + "\",\"customerId\":\"" + actualCustomerId
                + "\",\"refundType\":\"FULL\"}");

        chatRefundProcessorService.handleChatEvents(event);

        verify(supportTicketRepository, never()).save(any(SupportTicket.class));
        verify(refundService, never()).quote(any(), anyList());
        org.mockito.ArgumentCaptor<com.fooddelivery.common.outbox.entity.OutboxEventEntity> error =
                org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.outbox.entity.OutboxEventEntity.class);
        verify(outboxEventRepository).save(error.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.fooddelivery.common.constants.EventType.CHAT_REFUND_ERROR,
                error.getValue().getEventType());
    }

    @Test
    void derivesTicketCustomerFromTheOrderInsteadOfTheBrowserPayload() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID actualCustomerId = UUID.randomUUID();
        UUID forgedCustomerId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(actualCustomerId);
        order.setTotalAmount(new BigDecimal("100.00"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(refundService.quote(orderId, java.util.List.of())).thenReturn(new BigDecimal("10.00"));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket ticket = invocation.getArgument(0);
            ticket.setId(UUID.randomUUID());
            return ticket;
        });

        OutboxEvent event = new OutboxEvent();
        event.setType("CHAT_REFUND_REQUESTED");
        event.setAggregateId(UUID.randomUUID().toString());
        event.setPayload("{\"orderId\":\"" + orderId + "\",\"customerId\":\"" + forgedCustomerId + "\","
                + "\"actorId\":\"" + actualCustomerId + "\",\"actorType\":\"CUSTOMER\",\"refundType\":\"FULL\"}");

        chatRefundProcessorService.handleChatEvents(event);

        org.mockito.ArgumentCaptor<SupportTicket> ticket = org.mockito.ArgumentCaptor.forClass(SupportTicket.class);
        verify(supportTicketRepository).save(ticket.capture());
        org.junit.jupiter.api.Assertions.assertEquals(actualCustomerId, ticket.getValue().getCustomerId());
    }
    @Test
    void partialQuotePreservesTheValidatedItemsAndReason() throws Exception {
        UUID orderId=UUID.randomUUID(), customerId=UUID.randomUUID(), itemId=UUID.randomUUID();
        Order order=new Order();order.setId(orderId);order.setCustomerId(customerId);
        order.setTotalAmount(new BigDecimal("100.00"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(refundService.quote(eq(orderId),anyList())).thenReturn(new BigDecimal("25.00"));
        OutboxEvent event=new OutboxEvent();event.setId(UUID.randomUUID().toString());
        event.setType("CHAT_REFUND_QUOTE_REQUESTED");event.setAggregateId(UUID.randomUUID().toString());
        event.setPayload(objectMapper.writeValueAsString(java.util.Map.of(
                "orderId",orderId,"actorId",customerId,"actorType","CUSTOMER","refundType","PARTIAL",
                "reason","One missing portion","items",java.util.List.of(java.util.Map.of("itemId",itemId,"quantity",1)))));
        chatRefundProcessorService.handleChatEvents(event);
        var outbox=org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.outbox.entity.OutboxEventEntity.class);
        verify(outboxEventRepository).save(outbox.capture());
        org.junit.jupiter.api.Assertions.assertEquals(com.fooddelivery.common.constants.EventType.CHAT_REFUND_QUOTE_RESPONSE,outbox.getValue().getEventType());
        var payload=objectMapper.readTree(outbox.getValue().getPayload());
        org.junit.jupiter.api.Assertions.assertEquals(orderId.toString(),payload.get("orderId").asText());
        org.junit.jupiter.api.Assertions.assertEquals("One missing portion",payload.get("reason").asText());
        org.junit.jupiter.api.Assertions.assertEquals(itemId.toString(),payload.get("items").get(0).get("itemId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(1,payload.get("items").get(0).get("quantity").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("25.00"),payload.get("quoteAmount").decimalValue().setScale(2));
        verify(supportTicketRepository,never()).save(any());
        verify(refundService,never()).request(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "CHAT_REFUND_QUOTE_REQUESTED, PARTIAL, missing",
        "CHAT_REFUND_QUOTE_REQUESTED, PARTIAL, empty",
        "CHAT_REFUND_REQUESTED, PARTIAL, missing",
        "CHAT_REFUND_REQUESTED, PARTIAL, empty",
        "CHAT_REFUND_QUOTE_REQUESTED, UNKNOWN, empty",
        "CHAT_REFUND_REQUESTED, UNKNOWN, empty",
        "CHAT_REFUND_QUOTE_REQUESTED, FULL, selected",
        "CHAT_REFUND_REQUESTED, FULL, selected"
    })
    void invalidSelectionNeverExpandsIntoAFullRefund(String eventType,String refundType,String selection) throws Exception {
        UUID orderId=UUID.randomUUID(), customerId=UUID.randomUUID();
        Order order=new Order();order.setId(orderId);order.setCustomerId(customerId);
        order.setTotalAmount(new BigDecimal("100.00"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        java.util.Map<String,Object> payload=new java.util.HashMap<>();
        payload.put("orderId",orderId);payload.put("actorId",customerId);payload.put("actorType","CUSTOMER");
        payload.put("refundType",refundType);
        if(selection.equals("empty"))payload.put("items",java.util.List.of());
        if(selection.equals("selected"))payload.put("items",java.util.List.of(java.util.Map.of("itemId",UUID.randomUUID(),"quantity",1)));
        OutboxEvent event=new OutboxEvent();event.setId(UUID.randomUUID().toString());event.setType(eventType);
        event.setAggregateId(UUID.randomUUID().toString());event.setPayload(objectMapper.writeValueAsString(payload));
        chatRefundProcessorService.handleChatEvents(event);
        var outbox=org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.outbox.entity.OutboxEventEntity.class);
        verify(outboxEventRepository).save(outbox.capture());
        org.junit.jupiter.api.Assertions.assertEquals(com.fooddelivery.common.constants.EventType.CHAT_REFUND_ERROR,outbox.getValue().getEventType());
        verify(refundService,never()).quote(any(),any());
        verify(refundService,never()).request(any());
        verify(supportTicketRepository,never()).save(any());
    }

}
