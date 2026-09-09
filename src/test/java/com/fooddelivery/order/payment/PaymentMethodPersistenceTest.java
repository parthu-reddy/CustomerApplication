package com.fooddelivery.order.payment;

import com.fooddelivery.common.client.PaymentServiceClient;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.PaymentGateway;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.service.PaymentGatewayOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@code payment_method} existed as a column and as an entity field, and nothing ever wrote it. With
 * it null, {@code RefundService} routed every refund to store credit, the unpaid-order sweeper never
 * recovered a wallet debit, and COD never booked its cash. These tests pin the write.
 */
public class PaymentMethodPersistenceTest {

    private IPaymentIntentRepository intentRepository;
    private PaymentServiceClient paymentClient;
    private PaymentGatewayOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        intentRepository = mock(IPaymentIntentRepository.class);
        paymentClient = mock(PaymentServiceClient.class);
        orchestrator = new PaymentGatewayOrchestrator(intentRepository, mock(IOrderRepository.class), paymentClient);
    }

    private Order order() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("999.00"));
        return order;
    }

    private PaymentIntent savedIntent() {
        ArgumentCaptor<PaymentIntent> captor = ArgumentCaptor.forClass(PaymentIntent.class);
        verify(intentRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void walletIntentRecordsTheMethodAndNoGateway() {
        orchestrator.generateIntent(order(), PaymentMethod.WALLET);

        PaymentIntent intent = savedIntent();
        assertEquals(PaymentMethod.WALLET, intent.getPaymentMethod());
        assertNull(intent.getGatewayName(), "a wallet payment does not go through a gateway");
        assertEquals(PaymentIntentStatus.INITIATED, intent.getStatus());
    }

    @Test
    void codIntentIsPendingCollection() {
        orchestrator.generateIntent(order(), PaymentMethod.COD);

        PaymentIntent intent = savedIntent();
        assertEquals(PaymentMethod.COD, intent.getPaymentMethod());
        assertEquals(PaymentIntentStatus.PENDING_COLLECTION, intent.getStatus(),
                "cash has not been collected at checkout; marking it otherwise lets a cancellation pay out");
    }

    @Test
    void gatewayIntentRecordsBothTheMethodAndTheGateway() {
        when(paymentClient.createOrder(anyString(), any())).thenReturn("order_rzp_123");

        orchestrator.generateIntent(order(), PaymentMethod.CARD);

        PaymentIntent intent = savedIntent();
        assertEquals(PaymentMethod.CARD, intent.getPaymentMethod());
        assertEquals(PaymentGateway.RAZORPAY, intent.getGatewayName(),
                "the gateway must be recorded so a refund can be routed back to it");
        assertEquals("order_rzp_123", intent.getGatewayOrderId());
    }

    @Test
    void everyMethodIsRecorded() {
        for (PaymentMethod method : new PaymentMethod[]{PaymentMethod.WALLET, PaymentMethod.COD}) {
            reset(intentRepository);
            orchestrator.generateIntent(order(), method);
            assertEquals(method, savedIntent().getPaymentMethod(), "method not recorded for " + method);
        }
    }
}
