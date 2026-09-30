package com.fooddelivery.order.controller;

import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.SupportTicket;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.SupportTicketRepository;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOrderControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("d1000000-0000-4000-8000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("d1000000-0000-4000-8000-000000000002");

    @Mock
    private IOrderRepository orderRepository;
    @Mock
    private SupportTicketRepository supportTicketRepository;
    @Mock
    private com.fooddelivery.common.service.RateLimitingService rateLimitingService;
    @Mock
    private RefundService refundService;
    @Mock
    private Bucket bucket;

    @InjectMocks
    private CustomerOrderController controller;

    @BeforeEach
    void allowRequest() {
        when(rateLimitingService.resolveBucket(anyString(), anyInt(), anyInt(), any())).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);
    }

    @Test
    void postDeliveryRefundRequestStoresTheCanonicalFullOrderQuote() {
        Order order = deliveredOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(supportTicketRepository.existsByOrderIdAndCustomerIdAndStatus(
                ORDER_ID, CUSTOMER_ID, SupportTicket.TicketStatus.OPEN)).thenReturn(false);
        when(refundService.quote(ORDER_ID, List.of())).thenReturn(new BigDecimal("220.00"));

        var response = controller.requestPostDeliveryRefund(
                customer(), ORDER_ID, Map.of("reason", "The delivered meal was not usable"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<SupportTicket> ticketCaptor = ArgumentCaptor.forClass(SupportTicket.class);
        verify(supportTicketRepository).save(ticketCaptor.capture());
        SupportTicket ticket = ticketCaptor.getValue();
        assertThat(ticket.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(ticket.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(ticket.getReason()).isEqualTo("The delivered meal was not usable");
        assertThat(ticket.getRefundAmount()).isEqualByComparingTo("220.00");
        verify(refundService).quote(ORDER_ID, List.of());
    }

    @Test
    void duplicateOpenRequestDoesNotCreateOrQuoteAnotherTicket() {
        Order order = deliveredOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(supportTicketRepository.existsByOrderIdAndCustomerIdAndStatus(
                ORDER_ID, CUSTOMER_ID, SupportTicket.TicketStatus.OPEN)).thenReturn(true);

        var response = controller.requestPostDeliveryRefund(
                customer(), ORDER_ID, Map.of("reason", "The delivered meal was not usable"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(supportTicketRepository, never()).save(any());
        verify(refundService, never()).quote(any(), any());
    }

    private static Order deliveredOrder() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setCustomerId(CUSTOMER_ID);
        order.setDeliveryStatus(DeliveryStatus.DELIVERED);
        order.setTotalAmount(new BigDecimal("220.00"));
        return order;
    }

    private static Principal customer() {
        return () -> CUSTOMER_ID.toString();
    }
}
