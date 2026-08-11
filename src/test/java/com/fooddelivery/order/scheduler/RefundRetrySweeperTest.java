package com.fooddelivery.order.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.service.OrderRefundService;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefundRetrySweeperTest {

    @Mock
    private IPaymentIntentRepository paymentIntentRepository;

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private OrderRefundService orderRefundService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RefundRetrySweeper refundRetrySweeper;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldRetryFailedRefund_WhenRetryCountIsBelowMax() {
        // Arrange
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        UUID orderId = UUID.randomUUID();
        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setInternalOrderId(orderId);
        intent.setStatus(PaymentIntentStatus.REFUND_FAILED);
        intent.setRetryCount(2);

        Page<PaymentIntent> page = new PageImpl<>(List.of(intent));
        when(paymentIntentRepository.findByStatusAndCreatedAtBetween(
                eq(PaymentIntentStatus.REFUND_FAILED), any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)
        )).thenReturn(page);

        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // Act
        refundRetrySweeper.retryFailedRefunds();

        // Assert
        verify(paymentIntentRepository, times(1)).save(intent);
        verify(orderRefundService, times(1)).processRefund(order);
    }

    @Test
    void shouldEscalateToManualIntervention_WhenRetryCountReachesMax() throws Exception {
        // Arrange
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        UUID orderId = UUID.randomUUID();
        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setInternalOrderId(orderId);
        intent.setStatus(PaymentIntentStatus.REFUND_FAILED);
        intent.setRetryCount(5); // Max retries reached

        Page<PaymentIntent> page = new PageImpl<>(List.of(intent));
        when(paymentIntentRepository.findByStatusAndCreatedAtBetween(
                eq(PaymentIntentStatus.REFUND_FAILED), any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)
        )).thenReturn(page);

        // Act
        refundRetrySweeper.retryFailedRefunds();

        // Assert
        verify(paymentIntentRepository, times(1)).save(intent);
        verify(outboxEventRepository, times(1)).save(any());
        verify(orderRefundService, never()).processRefund(any()); // Should NOT process refund
    }

    @Test
    void shouldSkipRefund_WhenRedisLockNotAcquired() {
        // Arrange
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        // Act
        refundRetrySweeper.retryFailedRefunds();

        // Assert
        verify(paymentIntentRepository, never()).findByStatusAndCreatedAtBetween(any(), any(), any(), any());
        verify(orderRefundService, never()).processRefund(any());
    }
}
