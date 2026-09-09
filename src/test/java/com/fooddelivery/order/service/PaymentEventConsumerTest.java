package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.springframework.test.context.ActiveProfiles("contract-test")
public class PaymentEventConsumerTest {

    @Mock
    private IOrderRepository orderRepository;
    @Mock
    private IIdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private IPaymentIntentRepository paymentIntentRepository;
    @Mock
    private OrderActionService orderActionService;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private com.fooddelivery.order.ledger.LedgerBookkeeper ledgerBookkeeper;
    @Mock
    private com.fooddelivery.order.refund.RefundService refundService;

    private PaymentEventConsumer paymentEventConsumer;

    @BeforeEach
    void setUp() {
        paymentEventConsumer = new PaymentEventConsumer(
                idempotencyKeyRepository,
                transactionTemplate,
                objectMapper,
                paymentIntentRepository,
                orderRepository,
                orderActionService,
                outboxEventRepository,
                refundService,
                ledgerBookkeeper
        );

        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(Mockito.mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        lenient().when(idempotencyKeyRepository.existsById(anyString())).thenReturn(false);
    }

    @Test
    void consumePaymentEvent_LatePaymentWhenOrderCancelled_TriggersRefund() throws Exception {
        UUID orderId = UUID.randomUUID();
        String message = "{\"eventType\":\"PAYMENT_SUCCESS\",\"orderId\":\"" + orderId + "\",\"gatewayOrderId\":\"GATEWAY_123\"}";

        ObjectNode rootNode = new ObjectMapper().createObjectNode();
        rootNode.put("eventType", "PAYMENT_SUCCESS");
        rootNode.put("orderId", orderId.toString());
        rootNode.put("gatewayOrderId", "GATEWAY_123");
        rootNode.put("amount", "100.00");

        when(objectMapper.readTree(message)).thenReturn(rootNode);

        com.fooddelivery.order.entity.PaymentIntent intent = new com.fooddelivery.order.entity.PaymentIntent();
        intent.setInternalOrderId(orderId);
        when(paymentIntentRepository.findByGatewayOrderId("GATEWAY_123")).thenReturn(Optional.of(intent));

        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.CANCELLED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        java.util.Map<String, Object> headers = new java.util.HashMap<>();
        headers.put("eventId", java.util.UUID.randomUUID().toString());
        paymentEventConsumer.handlePaymentEvents(message, headers);

        // Verify that because order is CANCELLED, late payment triggers immediate refund
        verify(refundService).request(any(com.fooddelivery.order.refund.RefundCommand.class));
    }

    /** The consumer must not choose a destination; RefundService routes from the intent. */
    @Test
    void consumePaymentEvent_LatePaymentRefund_LeavesRoutingToRefundService() throws Exception {
        UUID orderId = UUID.randomUUID();
        String message = "{\"eventType\":\"PAYMENT_SUCCESS\",\"orderId\":\"" + orderId + "\"}";

        ObjectNode rootNode = new ObjectMapper().createObjectNode();
        rootNode.put("eventType", "PAYMENT_SUCCESS");
        rootNode.put("orderId", orderId.toString());
        rootNode.put("gatewayOrderId", "GATEWAY_123");
        when(objectMapper.readTree(message)).thenReturn(rootNode);

        com.fooddelivery.order.entity.PaymentIntent intent = new com.fooddelivery.order.entity.PaymentIntent();
        intent.setInternalOrderId(orderId);
        when(paymentIntentRepository.findByGatewayOrderId("GATEWAY_123")).thenReturn(Optional.of(intent));

        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        java.util.Map<String, Object> headers = new java.util.HashMap<>();
        headers.put("eventId", UUID.randomUUID().toString());
        paymentEventConsumer.handlePaymentEvents(message, headers);

        org.mockito.ArgumentCaptor<com.fooddelivery.order.refund.RefundCommand> cmd =
                org.mockito.ArgumentCaptor.forClass(com.fooddelivery.order.refund.RefundCommand.class);
        verify(refundService).request(cmd.capture());
        org.junit.jupiter.api.Assertions.assertNull(cmd.getValue().getDestination(),
                "a system caller that picks the destination bypasses the matrix -- a wallet-paid "
                + "order was pushed at a gateway that never took the money");
        org.junit.jupiter.api.Assertions.assertEquals(
                com.fooddelivery.order.enums.InitiatorType.SYSTEM, cmd.getValue().getInitiatorType());
    }

    /**
     * A refund the matrix refuses must not unwind the payment-state transition. Before this, the
     * exception rolled back the transaction and Kafka redelivered the same event forever, blocking
     * every other order on the partition.
     */
    @Test
    void consumePaymentEvent_RefundRoutingRefused_DoesNotPropagate() throws Exception {
        UUID orderId = UUID.randomUUID();
        String message = "{\"eventType\":\"PAYMENT_SUCCESS\",\"orderId\":\"" + orderId + "\"}";

        ObjectNode rootNode = new ObjectMapper().createObjectNode();
        rootNode.put("eventType", "PAYMENT_SUCCESS");
        rootNode.put("orderId", orderId.toString());
        rootNode.put("gatewayOrderId", "GATEWAY_123");
        when(objectMapper.readTree(message)).thenReturn(rootNode);

        com.fooddelivery.order.entity.PaymentIntent intent = new com.fooddelivery.order.entity.PaymentIntent();
        intent.setInternalOrderId(orderId);
        when(paymentIntentRepository.findByGatewayOrderId("GATEWAY_123")).thenReturn(Optional.of(intent));

        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        when(refundService.request(any())).thenThrow(new IllegalStateException("REFUND_STATE_INVALID"));

        java.util.Map<String, Object> headers = new java.util.HashMap<>();
        headers.put("eventId", UUID.randomUUID().toString());

        // Must not throw: the state change is the more important of the two and is kept.
        paymentEventConsumer.handlePaymentEvents(message, headers);

        verify(refundService).request(any());
    }

    /** A duplicate delivery must be ignored rather than refunded twice. */
    @Test
    void consumePaymentEvent_DuplicateEventIsIgnored() throws Exception {
        UUID orderId = UUID.randomUUID();
        String message = "{\"eventType\":\"PAYMENT_SUCCESS\",\"orderId\":\"" + orderId + "\"}";
        when(idempotencyKeyRepository.existsById(anyString())).thenReturn(true);

        java.util.Map<String, Object> headers = new java.util.HashMap<>();
        headers.put("eventId", UUID.randomUUID().toString());
        paymentEventConsumer.handlePaymentEvents(message, headers);

        verify(refundService, never()).request(any());
        verify(orderRepository, never()).findById(any());
    }
}
