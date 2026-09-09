package com.fooddelivery.customer.service;

import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.customer.dto.CustomerReceipt;
import com.fooddelivery.customer.service.money.CustomerReceiptService;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class CustomerReceiptTest {

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private RefundRepository refundRepository;

    @InjectMocks
    private CustomerReceiptService customerReceiptService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetReceiptSuccess() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setDeliveryFee(new BigDecimal("2.50"));
        order.setCustomerPlatformFee(new BigDecimal("1.50"));
        order.setSgst(new BigDecimal("0.50"));
        order.setCgst(new BigDecimal("0.50"));
        order.setTotalAmount(new BigDecimal("15.00"));
        order.setPaymentMethod(PaymentMethod.CARD);
        order.setCreatedAt(LocalDateTime.now());

        OrderItem item = new OrderItem();
        item.setMenuItemId(UUID.randomUUID());
        item.setPrice(new BigDecimal("5.00"));
        item.setQuantity(2);
        order.setOrderItems(Set.of(item));

        when(orderRepository.findById(eq(orderId))).thenReturn(Optional.of(order));
        when(refundRepository.findByOrderId(eq(orderId))).thenReturn(List.of());

        CustomerReceipt receipt = customerReceiptService.getReceipt(orderId, customerId);

        assertNotNull(receipt);
        assertEquals(new BigDecimal("2.50"), receipt.getDeliveryFee());
        assertEquals(new BigDecimal("1.50"), receipt.getPlatformFee());
        assertEquals(new BigDecimal("10.00"), receipt.getItemTotal());
        assertEquals(new BigDecimal("15.00"), receipt.getTotal());
        assertEquals(1, receipt.getItems().size());
        assertEquals(0, receipt.getRefunds().size());
    }

    @Test
    void testGetReceiptForbidden() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Order order = new Order();
        order.setCustomerId(UUID.randomUUID()); // different user

        when(orderRepository.findById(eq(orderId))).thenReturn(Optional.of(order));

        assertThrows(ResponseStatusException.class, () -> customerReceiptService.getReceipt(orderId, customerId));
    }
}
