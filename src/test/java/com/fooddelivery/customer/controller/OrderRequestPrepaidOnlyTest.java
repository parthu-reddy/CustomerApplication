package com.fooddelivery.customer.controller;

import com.fooddelivery.common.contract.PlatformJson;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.exception.GlobalExceptionHandler;
import com.fooddelivery.common.service.RateLimitingService;
import com.fooddelivery.customer.dto.OrderRequest;
import com.fooddelivery.customer.service.CustomerOrderService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cash on delivery is not allowed on this platform (retired 2026-09-16; the owner's rule since
 * 2026-09-26 is that it is never allowed). PaymentMethod has no COD constant, so an order naming it
 * must be refused at the API boundary, before any order or payment is created.
 *
 * <p>Named *PrepaidOnlyTest: readiness check 1.5 forbids "COD" everywhere else in the codebase, and
 * this test has to say it to prove it is refused.
 */
class OrderRequestPrepaidOnlyTest {

    private final CustomerOrderService orders = mock(CustomerOrderService.class);
    private final RateLimitingService rateLimits = mock(RateLimitingService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(orders, rateLimits))
            .setControllerAdvice(new GlobalExceptionHandler())
            // Boot's Jackson configuration, which a standalone MockMvc would otherwise not have.
            .setMessageConverters(PlatformJson.messageConverters())
            .build();
    private final UUID customerId = UUID.randomUUID();
    private final Principal customer = customerId::toString;

    private String order(String paymentMethod) {
        return """
                {"quoteId":"%s","customerId":"%s","restaurantId":"%s","deliveryAddressId":"%s",
                 "paymentMethod":"%s","items":[{"menuItemId":"%s","quantity":1}]}
                """.formatted(UUID.randomUUID(), customerId, UUID.randomUUID(), UUID.randomUUID(),
                paymentMethod, UUID.randomUUID());
    }

    @Test
    void anOrderPaidCashOnDelivery_isRefusedBeforeAnythingIsCreated() throws Exception {
        mvc.perform(post("/api/v1/orders").principal(customer)
                        .contentType(MediaType.APPLICATION_JSON).content(order("COD")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orders);
    }

    /** The control for the test above: the same body with a prepaid method gets through, so the 400 is about COD. */
    @Test
    void theSameOrderPaidByUpi_reachesTheOrderService() throws Exception {
        Bucket bucket = mock(Bucket.class);
        when(bucket.tryConsume(1)).thenReturn(true);
        when(rateLimits.resolveBucket(anyString(), anyInt(), anyInt(), any())).thenReturn(bucket);
        when(orders.createOrderWithPayment(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("stop")));

        mvc.perform(post("/api/v1/orders").principal(customer)
                .contentType(MediaType.APPLICATION_JSON).content(order("UPI")));

        ArgumentCaptor<OrderRequest> placed = ArgumentCaptor.forClass(OrderRequest.class);
        verify(orders).createOrderWithPayment(placed.capture());
        assertThat(placed.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.UPI);
    }

    @Test
    void everyPaymentMethodThePlatformTakes_isPrepaid() {
        assertThat(PaymentMethod.values()).containsExactlyInAnyOrder(PaymentMethod.CARD, PaymentMethod.UPI, PaymentMethod.WALLET);
    }
}
