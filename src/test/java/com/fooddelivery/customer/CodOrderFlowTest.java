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
import java.util.UUID;

public class CodOrderFlowTest {

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
    void testCodFlow_SavesPaymentCompletedEvent() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        
        checkoutService.processWalletOrCod(order, PaymentMethod.COD, "INTERNAL_INTENT");

        verifyNoInteractions(walletClient);
        
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepo, times(1)).save(captor.capture());
        
        OutboxEventEntity saved = captor.getValue();
        assertEquals("PAYMENT", saved.getAggregateType().name());
        assertTrue(saved.getPayload().contains("PAYMENT_COMPLETED"));
        assertTrue(saved.getPayload().contains("INTERNAL_INTENT"));
    }
}
