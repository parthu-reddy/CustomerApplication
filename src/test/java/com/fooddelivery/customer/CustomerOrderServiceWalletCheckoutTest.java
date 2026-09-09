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

    /**
     * COD takes no money at checkout. Debiting a wallet here would charge a customer who chose to
     * pay the rider in cash.
     */
    @Test
    void testCodCheckout_TakesNoMoneyButStillSignalsTheOrder() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("150.00"));

        checkoutService.processWalletOrCod(order, PaymentMethod.COD, "INTERNAL_" + UUID.randomUUID());

        verify(walletClient, never()).debit(any(), any(), any(), any());
        // The order still has to move on: CreatedState routes COD to handleCodPlaced from here.
        verify(outboxRepo, times(1)).save(any(OutboxEventEntity.class));
    }

    /** A card or UPI order is settled by the gateway; this path must not touch it. */
    @Test
    void testCardCheckout_IsNotHandledHere() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("150.00"));

        checkoutService.processWalletOrCod(order, PaymentMethod.CARD, "gw_order_1");

        verify(walletClient, never()).debit(any(), any(), any(), any());
        verify(outboxRepo, never()).save(any());
    }

    /** The wallet debit is keyed on the intent, which is what UnpaidOrderCanceller looks up. */
    @Test
    void testWalletDebit_IsReferencedByTheIntentId() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("150.00"));

        UUID intentUuid = UUID.randomUUID();
        checkoutService.processWalletOrCod(order, PaymentMethod.WALLET, "INTERNAL_" + intentUuid);

        ArgumentCaptor<TransactionRequest> tx = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(walletClient).debit(eq("CUSTOMER"), eq(order.getCustomerId()), tx.capture(), anyString());
        assertEquals(intentUuid, tx.getValue().getReferenceId(),
                "the canceller recovers a stranded debit by this reference");
    }

    /**
     * A wallet debit that fails must announce no payment.
     *
     * <p>Note what this does <em>not</em> cover: whether the outbox write sits inside the
     * {@code transactionTemplate} lambda. With a mocked template the exception short-circuits both
     * arrangements identically, so moving the save outside the lambda leaves this green -- checked
     * on 2026-09-09 performing Phase 3's break-test 3. That placement is a structural property and
     * is held by validate_phase3.py check 9, which parses the lambda body and does go red.
     */
    @Test
    void testWalletDebitThatFails_SavesNoPaymentCompletedEvent() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new java.math.BigDecimal("250.00"));

        org.mockito.Mockito.doThrow(new RuntimeException("INSUFFICIENT_BALANCE"))
                .when(walletClient).debit(any(), any(), any(), any());

        String intent = UUID.randomUUID().toString();
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> checkoutService.processWalletOrCod(order, PaymentMethod.WALLET, intent));

        verify(outboxRepo, never()).save(any());
    }
}
