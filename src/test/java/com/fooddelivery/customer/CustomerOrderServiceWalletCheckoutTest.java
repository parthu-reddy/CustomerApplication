package com.fooddelivery.customer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fooddelivery.customer.service.WalletCheckoutService;
import com.fooddelivery.common.client.WalletServiceClient;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.dto.wallet.TransactionRequest;
import java.util.UUID;
import java.math.BigDecimal;

public class CustomerOrderServiceWalletCheckoutTest {

    private WalletCheckoutService checkoutService;
    private WalletServiceClient walletClient;
    private OutboxEventRepository outboxRepo;

    @BeforeEach
    void setUp() {
        walletClient = mock(WalletServiceClient.class);
        outboxRepo = mock(OutboxEventRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        checkoutService = new WalletCheckoutService(walletClient, transactionTemplate, outboxRepo);
    }

    @Test
    void testWalletCheckout_DebitsWalletAndSavesEvent() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("150.00"));
        
        String intent = "INTERNAL_" + UUID.randomUUID().toString();

        checkoutService.processWalletOrCod(order, PaymentMethod.WALLET, intent);

        ArgumentCaptor<TransactionRequest> txCaptor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(walletClient, times(1)).debit(eq("CUSTOMER"), eq(order.getCustomerId()), txCaptor.capture(), anyString());
        
        TransactionRequest capturedTx = txCaptor.getValue();
        assertEquals(new BigDecimal("150.00"), capturedTx.getAmount());
        assertEquals("ORDER_TOTAL", capturedTx.getCategory().name());
        
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepo, times(1)).save(outboxCaptor.capture());
        
        OutboxEventEntity saved = outboxCaptor.getValue();
        assertEquals("PAYMENT", saved.getAggregateType().name());
        assertTrue(saved.getPayload().contains("PAYMENT_COMPLETED"));
        assertTrue(saved.getPayload().contains(intent));
    }
}
