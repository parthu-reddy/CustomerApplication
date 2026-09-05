package com.fooddelivery.order.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fooddelivery.order.service.PaymentGatewayOrchestrator;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.common.client.PaymentServiceClient;
import com.fooddelivery.common.dto.payment.CreateOrderRequest;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentGatewayOrchestratorTest {

    private PaymentGatewayOrchestrator orchestrator;
    private IPaymentIntentRepository intentRepo;
    private IOrderRepository orderRepo;
    private PaymentServiceClient client;

    @BeforeEach
    void setUp() {
        intentRepo = mock(IPaymentIntentRepository.class);
        orderRepo = mock(IOrderRepository.class);
        client = mock(PaymentServiceClient.class);
        orchestrator = new PaymentGatewayOrchestrator(intentRepo, orderRepo, client);
    }

    @Test
    void testGenerateIntent_WalletBypass() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("100.00"));

        String result = orchestrator.generateIntent(order, PaymentMethod.WALLET);

        assertTrue(result.startsWith("INTERNAL_"));
        verify(intentRepo, times(1)).save(any(PaymentIntent.class));
        verifyNoInteractions(client);
    }

    @Test
    void testGenerateIntent_ExternalGateway() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("200.00"));

        when(client.createOrder(anyString(), any(CreateOrderRequest.class))).thenReturn("ext_gateway_id_123");

        String result = orchestrator.generateIntent(order, PaymentMethod.UPI);

        assertEquals("ext_gateway_id_123", result);
        verify(intentRepo, times(1)).save(any(PaymentIntent.class));
        verify(client, times(1)).createOrder(anyString(), any(CreateOrderRequest.class));
    }
}
