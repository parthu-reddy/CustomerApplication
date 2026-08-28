package com.fooddelivery.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.enums.FaultType;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.SupportTicket;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.SupportTicketRepository;
import com.fooddelivery.order.service.OrderRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRefundControllerTest {

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private OrderRefundService orderRefundService;

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private com.fooddelivery.common.service.RateLimitingService rateLimitingService;

    @InjectMocks
    private AdminRefundController adminRefundController;

    private UUID ticketId;
    private UUID orderId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    @Test
    void testPartialRefundSummingToTotalIsFullRefund() {
        // Arrange
        SupportTicket ticket = new SupportTicket();
        ticket.setId(ticketId);
        ticket.setOrderId(orderId);
        ticket.setStatus(SupportTicket.TicketStatus.OPEN);
        // Refund amount is 10.10
        ticket.setRefundAmount(new BigDecimal("10.10"));

        Order order = new Order();
        order.setId(orderId);
        // Order total is also 10.10 but with different scale
        order.setTotalAmount(new BigDecimal("10.100"));

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        AdminRefundController.ResolveRequest request = new AdminRefundController.ResolveRequest(true, "Approved", "RESTAURANT_FAULT", null);

        io.github.bucket4j.Bucket bucket = mock(io.github.bucket4j.Bucket.class);
        when(rateLimitingService.resolveBucket(anyString(), anyInt(), anyInt(), any())).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        // Act
        ResponseEntity<SupportTicket> response = adminRefundController.resolveTicket(ticketId, request, adminId);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(SupportTicket.TicketStatus.RESOLVED, ticket.getStatus());

        verify(orderRefundService, times(1)).processRefund(order, RefundDestination.GATEWAY, FaultType.RESTAURANT_FAULT);
        verify(orderRefundService, never()).processPartialRefund(any(), any(), any(), any());
    }

    @Test
    void testRateLimitingReturns429() {
        // Arrange
        com.fooddelivery.common.service.RateLimitingService rateLimitingService = mock(com.fooddelivery.common.service.RateLimitingService.class);
        AdminRefundController controller = new AdminRefundController(supportTicketRepository, orderRefundService, orderRepository, objectMapper, rateLimitingService);
        
        io.github.bucket4j.Bucket bucket = mock(io.github.bucket4j.Bucket.class);
        when(rateLimitingService.resolveBucket(anyString(), anyInt(), anyInt(), any())).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false);

        AdminRefundController.ResolveRequest request = new AdminRefundController.ResolveRequest(true, "Approved", "RESTAURANT_FAULT", null);

        // Act
        ResponseEntity<SupportTicket> response = controller.resolveTicket(ticketId, request, adminId);

        // Assert
        assertEquals(429, response.getStatusCodeValue());
    }

    @Test
    void overrideBelowTheQuoteRefundsTheOverriddenAmount() {
        SupportTicket ticket = new SupportTicket();
        ticket.setId(ticketId);
        ticket.setOrderId(orderId);
        ticket.setStatus(SupportTicket.TicketStatus.OPEN);
        ticket.setRefundAmount(new BigDecimal("10.10"));

        Order order = new Order();
        order.setId(orderId);
        order.setTotalAmount(new BigDecimal("10.10"));

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        io.github.bucket4j.Bucket bucket = mock(io.github.bucket4j.Bucket.class);
        when(rateLimitingService.resolveBucket(anyString(), anyInt(), anyInt(), any())).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        AdminRefundController.ResolveRequest request =
                new AdminRefundController.ResolveRequest(true, "Approved", "RESTAURANT_FAULT", new BigDecimal("5.00"));

        ResponseEntity<SupportTicket> response = adminRefundController.resolveTicket(ticketId, request, adminId);

        assertEquals(200, response.getStatusCodeValue());
        // The override must reach the refund, not the original quote, and must be written back to
        // the ticket -- otherwise the ledger and the ticket disagree about what was refunded.
        assertEquals(new BigDecimal("5.00"), ticket.getRefundAmount());
        verify(orderRefundService).processPartialRefund(
                order, new BigDecimal("5.00"), RefundDestination.GATEWAY, FaultType.RESTAURANT_FAULT);
        verify(orderRefundService, never()).processRefund(any(), any(), any());
    }

    @Test
    void overrideAboveTheQuoteIsRejectedAndRefundsNothing() {
        SupportTicket ticket = new SupportTicket();
        ticket.setId(ticketId);
        ticket.setOrderId(orderId);
        ticket.setStatus(SupportTicket.TicketStatus.OPEN);
        ticket.setRefundAmount(new BigDecimal("10.10"));

        Order order = new Order();
        order.setId(orderId);
        order.setTotalAmount(new BigDecimal("10.10"));

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        io.github.bucket4j.Bucket bucket = mock(io.github.bucket4j.Bucket.class);
        when(rateLimitingService.resolveBucket(anyString(), anyInt(), anyInt(), any())).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        AdminRefundController.ResolveRequest request =
                new AdminRefundController.ResolveRequest(true, "Approved", "RESTAURANT_FAULT", new BigDecimal("20.00"));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> adminRefundController.resolveTicket(ticketId, request, adminId));
        assertEquals("Override amount cannot be greater than original quote", thrown.getMessage());

        // The guard exists to stop an admin refunding more than was quoted: nothing may be paid out.
        verify(orderRefundService, never()).processRefund(any(), any(), any());
        verify(orderRefundService, never()).processPartialRefund(any(), any(), any(), any());
    }
}
