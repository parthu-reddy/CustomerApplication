package com.fooddelivery.customer.controller;

import com.fooddelivery.common.contract.PlatformJson;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.exception.GlobalExceptionHandler;
import com.fooddelivery.common.service.RateLimitingService;
import com.fooddelivery.customer.service.CustomerOrderService;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reading an order that is not the caller's answers 404, the same as an order that does not exist,
 * so the response neither reveals the order nor reports a server error. It used to throw a bare
 * RuntimeException, which the shared handler turned into a 500 logged at ERROR (seen live
 * 2026-10-03 by ChatAndRefundIsolationTest). The real service method and exception handler run
 * here; only the repository is mocked.
 */
@ExtendWith(MockitoExtension.class)
class OrderReadOwnershipTest {

    @Mock IOrderRepository orderRepository;
    @InjectMocks CustomerOrderService orders;

    private final UUID caller = UUID.randomUUID();
    private final Principal principal = caller::toString;

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new OrderController(orders, mock(RateLimitingService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(PlatformJson.messageConverters())
                .build();
    }

    @Test
    void anotherCustomersOrderIsNotFound() throws Exception {
        UUID someoneElsesOrder = UUID.randomUUID();
        when(orderRepository.findByIdAndCustomerId(someoneElsesOrder, caller)).thenReturn(Optional.empty());

        mvc().perform(get("/api/v1/orders/" + someoneElsesOrder).principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    /** Control: the caller's own order is read through the same path. */
    @Test
    void theOwnerReadsTheirOrder() throws Exception {
        Order own = new Order();
        own.setId(UUID.randomUUID());
        own.setCustomerId(caller);
        own.setStatus(OrderStatus.PREPARING);
        own.setTotalAmount(new BigDecimal("53.53"));
        own.setItemTotal(new BigDecimal("28.37"));
        own.setSgst(new BigDecimal("0.71"));
        own.setCgst(new BigDecimal("0.71"));
        own.setDeliveryFee(new BigDecimal("18.74"));
        own.setCustomerPlatformFee(new BigDecimal("5.00"));
        when(orderRepository.findByIdAndCustomerId(own.getId(), caller)).thenReturn(Optional.of(own));

        mvc().perform(get("/api/v1/orders/" + own.getId()).principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(own.getId().toString()));
    }
}
