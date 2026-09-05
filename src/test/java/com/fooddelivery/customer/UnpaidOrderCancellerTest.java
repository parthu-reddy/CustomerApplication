package com.fooddelivery.customer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fooddelivery.order.scheduler.UnpaidOrderCanceller;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import com.fooddelivery.common.client.WalletServiceClient;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import org.springframework.http.ResponseEntity;
import com.fooddelivery.common.dto.ApiResponse;

import java.util.Collections;
import java.util.UUID;
import java.time.LocalDateTime;

public class UnpaidOrderCancellerTest {

    private UnpaidOrderCanceller canceller;
    private IOrderRepository orderRepo;
    private OrderActionService actionService;
    private StringRedisTemplate redisTemplate;
    private IPaymentIntentRepository intentRepo;
    private WalletServiceClient walletClient;
    private OrderSagaOrchestrator sagaOrchestrator;
    private OutboxEventRepository outboxRepo;

    @BeforeEach
    void setUp() {
        orderRepo = mock(IOrderRepository.class);
        actionService = mock(OrderActionService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        intentRepo = mock(IPaymentIntentRepository.class);
        walletClient = mock(WalletServiceClient.class);
        sagaOrchestrator = mock(OrderSagaOrchestrator.class);
        outboxRepo = mock(OutboxEventRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        canceller = new UnpaidOrderCanceller(orderRepo, actionService, transactionTemplate, redisTemplate, intentRepo, walletClient, sagaOrchestrator, outboxRepo);
    }

    @Test
    void testSweepStaleCreatedOrders_CancelsOrder() throws Exception {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.CREATED);
        order.setPaymentMethod(PaymentMethod.CARD);

        when(orderRepo.findByStatusAndUpdatedAtBefore(eq(OrderStatus.CREATED), any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(order)));
        

        canceller.sweepStaleCreatedOrders();

        verify(sagaOrchestrator, times(1)).cancelOrderLocally(eq(order), anyString());
    }
}
