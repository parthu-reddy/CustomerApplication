package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
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
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private IPaymentIntentRepository paymentIntentRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private com.fooddelivery.customer.client.PaymentClient paymentClient;

    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    private OrderSagaOrchestrator orderSagaOrchestrator;

    private com.fooddelivery.order.service.state.OrderActionService orderActionService;

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
        orderActionService = new com.fooddelivery.order.service.state.OrderActionService(
                orderRepository,
                outboxEventRepository,
                paymentIntentRepository,
                objectMapper
        );

        orderSagaOrchestrator = new OrderSagaOrchestrator(
                orderRepository,
                outboxEventRepository,
                paymentIntentRepository,
                objectMapper,
                kafkaTemplate,
                paymentClient,
                transactionTemplate,
                orderActionService,
                redisTemplate
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
        assertThat(savedOutbox.getAggregateType()).isEqualTo(com.fooddelivery.common.constants.AggregateType.ORDER);
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
        when(paymentIntentRepository.findByInternalOrderId(internalOrderId)).thenReturn(Optional.of(intent));
        when(orderRepository.findById(internalOrderId)).thenReturn(Optional.of(order));
        
        // Mock redis ops for idempotency
        org.springframework.data.redis.core.ValueOperations valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(true);
        
        // Act
        java.util.Map<String, Object> headers = new java.util.HashMap<>();
        headers.put("eventId", UUID.randomUUID().toString());
        orderSagaOrchestrator.handlePaymentEvents(payload, headers);

        verify(orderRepository).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_ACCEPTANCE);
        
        verify(paymentIntentRepository).save(intent);
        assertThat(intent.getStatus()).isEqualTo(com.fooddelivery.common.constants.PaymentIntentStatus.SUCCESS);

        verify(outboxEventRepository, times(2)).save(any(OutboxEventEntity.class));
    }

    @Test
    void handleOrderEvents_ShouldUpdateOrder_WhenOrderAccepted() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).status(OrderStatus.PENDING_ACCEPTANCE).build();

        String payload = String.format("{\"orderId\": \"%s\"}", orderId.toString());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        // Mock redis ops for idempotency
        org.springframework.data.redis.core.ValueOperations valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(true);
        
        java.util.Map<String, Object> headers = new java.util.HashMap<>();
        headers.put("eventType", com.fooddelivery.common.constants.EventType.ORDER_ACCEPTED.name());
        headers.put("eventId", UUID.randomUUID().toString());
        orderSagaOrchestrator.handleOrderEvents(payload, headers);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        verify(orderRepository).save(order);
    }
}
