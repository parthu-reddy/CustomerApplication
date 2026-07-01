package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OutboxEventEntity;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IOutboxEventRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private IOutboxEventRepository outboxEventRepository;

    @Mock
    private IPaymentIntentRepository paymentIntentRepository;

    @Mock
    private DoubleEntryLedgerService ledgerService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private org.springframework.web.client.RestTemplate restTemplate;

    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    private OrderSagaOrchestrator orderSagaOrchestrator;

    @BeforeEach
    void setUp() {
        // Mock executeWithoutResult to immediately run the lambda
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(org.mockito.ArgumentMatchers.any());

        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(org.mockito.ArgumentMatchers.any());

        orderSagaOrchestrator = new OrderSagaOrchestrator(
                orderRepository,
                outboxEventRepository,
                paymentIntentRepository,
                objectMapper,
                kafkaTemplate,
                ledgerService,
                restTemplate,
                transactionTemplate
        );
    }

    @Test
    void startOrderSaga_ShouldSaveOrderAndOutboxEvent() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).totalAmount(new BigDecimal("100.00")).build();

        when(orderRepository.save(order)).thenReturn(order);

        Order savedOrder = orderSagaOrchestrator.startOrderSaga(order);

        assertThat(savedOrder).isNotNull();
        verify(orderRepository).save(order);
        
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        
        OutboxEventEntity savedOutbox = outboxCaptor.getValue();
        assertThat(savedOutbox.getEventType()).isEqualTo(com.fooddelivery.common.constants.EventType.ORDER_CREATED);
        assertThat(savedOutbox.getAggregateType()).isEqualTo(com.fooddelivery.common.constants.AppConstants.AGGREGATE_ORDER);
    }

    @Test
    void handlePaymentSuccess_ShouldUpdateOrderToPaid() throws Exception {
        String gatewayOrderId = "pay_123";
        UUID internalOrderId = UUID.randomUUID();
        
        String payload = """
                {
                    "orderId": "%s",
                    "gatewayOrderId": "pay_123",
                    "amount": 10000,
                    "gatewayName": "VYAPAR"
                }
                """.formatted(internalOrderId.toString());

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setInternalOrderId(internalOrderId);
        intent.setStatus(com.fooddelivery.common.constants.PaymentIntentStatus.CREATED);

        Order order = Order.builder().id(internalOrderId).customerId(UUID.randomUUID()).status(OrderStatus.CREATED).build();

        when(paymentIntentRepository.findByGatewayOrderId(gatewayOrderId)).thenReturn(Optional.of(intent));
        when(orderRepository.findById(internalOrderId)).thenReturn(Optional.of(order));
        // Act
        orderSagaOrchestrator.handlePaymentEvents(payload);

        verify(orderRepository).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        
        verify(paymentIntentRepository).save(intent);
        assertThat(intent.getStatus()).isEqualTo(com.fooddelivery.common.constants.PaymentIntentStatus.SUCCESS);

        verify(outboxEventRepository, times(2)).save(any(OutboxEventEntity.class));
    }

    @Test
    void handleOrderEvents_ShouldUpdateOrder_WhenOrderAccepted() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).status(OrderStatus.PAID).build();

        String payload = String.format("{\"orderId\": \"%s\"}", orderId.toString());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        orderSagaOrchestrator.handleOrderEvents(payload, com.fooddelivery.common.constants.EventType.ORDER_ACCEPTED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        verify(orderRepository).save(order);
    }
}
