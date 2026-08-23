package com.fooddelivery.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.SupportTicketRepository;
import com.fooddelivery.order.service.OrderRefundService;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminOrderManualControllerTest {

    private IOrderRepository orderRepository;
    private OutboxEventRepository outboxEventRepository;
    private OrderSagaOrchestrator orderSagaOrchestrator;
    private OrderRefundService orderRefundService;
    private SupportTicketRepository supportTicketRepository;
    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;
    
    private AdminOrderManualController controller;

    @BeforeEach
    void setUp() {
        orderRepository = mock(IOrderRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        orderSagaOrchestrator = mock(OrderSagaOrchestrator.class);
        orderRefundService = mock(OrderRefundService.class);
        supportTicketRepository = mock(SupportTicketRepository.class);
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        
        controller = new AdminOrderManualController(
                orderRepository,
                outboxEventRepository,
                orderSagaOrchestrator,
                orderRefundService,
                supportTicketRepository
        );
    }

    @Test
    void reversalPublishFailure_failsTheRequest_andDoesNotLogSuccess() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setRestaurantId(UUID.randomUUID());
        
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        // Mock the failure for outbox save
        when(outboxEventRepository.save(any())).thenThrow(new DataIntegrityViolationException("boom"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", "10.00");
        payload.put("faultAttribution", "RESTAURANT");

        assertThatThrownBy(() -> controller.postDeliveryRefund(orderId, payload))
            .isInstanceOf(RuntimeException.class);

        // the customer refund must not have committed either
        verify(orderRefundService, never()).processPartialRefund(any(), any(), any());
    }
}
