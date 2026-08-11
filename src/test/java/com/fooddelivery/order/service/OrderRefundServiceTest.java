package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderRefundServiceTest {

    @Mock
    private IPaymentIntentRepository paymentIntentRepository;

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OrderActionService orderActionService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ObjectMapper objectMapper;
    private OrderRefundService orderRefundService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // Mock transaction template to just execute the callback
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        orderRefundService = new OrderRefundService(
                paymentIntentRepository,
                orderRepository,
                outboxEventRepository,
                orderActionService,
                transactionTemplate,
                objectMapper
        );
    }

    @Test
    void processRefund_Success() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmount(BigDecimal.ZERO);

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setStatus(PaymentIntentStatus.SUCCESS);
        intent.setGatewayOrderId("gateway_123");
        intent.setGatewayName("RAZORPAY");

        when(paymentIntentRepository.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderRefundService.processRefund(order);

        verify(outboxEventRepository, times(1)).save(any());
        assertEquals(PaymentIntentStatus.REFUND_PENDING, intent.getStatus());
        assertEquals(PaymentIntentStatus.REFUND_PENDING, order.getPaymentStatus());
        verify(paymentIntentRepository, times(1)).save(intent);
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void processRefund_AlreadyRefunded() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmount(new BigDecimal("100.00"));

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());

        when(paymentIntentRepository.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));

        orderRefundService.processRefund(order);

        // Should skip processing because remainingRefundable <= 0
        verify(outboxEventRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void processPartialRefund_Success() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmount(BigDecimal.ZERO);

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setStatus(PaymentIntentStatus.CAPTURED);
        intent.setGatewayOrderId("gateway_123");
        intent.setGatewayName("RAZORPAY");

        when(paymentIntentRepository.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderRefundService.processPartialRefund(order, new BigDecimal("40.00"));

        verify(outboxEventRepository, times(1)).save(any());
        assertEquals(PaymentIntentStatus.REFUND_PENDING, intent.getStatus());
        assertEquals(PaymentIntentStatus.REFUND_PENDING, order.getPaymentStatus());
        verify(paymentIntentRepository, times(1)).save(intent);
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void processPartialRefund_AmountExceedsRemaining() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmount(new BigDecimal("80.00"));

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setStatus(PaymentIntentStatus.CAPTURED);

        when(paymentIntentRepository.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            orderRefundService.processPartialRefund(order, new BigDecimal("40.00"));
        });

        assertTrue(ex.getMessage().contains("exceeds remaining refundable balance"));
    }
}
