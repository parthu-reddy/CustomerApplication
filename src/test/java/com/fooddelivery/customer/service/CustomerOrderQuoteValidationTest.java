package com.fooddelivery.customer.service;

import com.fooddelivery.customer.dto.OrderRequest;
import com.fooddelivery.customer.dto.OrderItemRequest;
import com.fooddelivery.customer.entity.CustomerAddress;
import com.fooddelivery.customer.repository.CustomerAddressRepository;
import com.fooddelivery.customer.client.RestaurantClient;
import com.fooddelivery.common.client.MapsServiceClient;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.customer.exception.QuoteExpiredException;
import com.fooddelivery.order.entity.OrderQuote;
import com.fooddelivery.order.entity.OrderQuoteItem;
import com.fooddelivery.order.repository.OrderQuoteRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import com.fooddelivery.order.service.PaymentGatewayOrchestrator;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reject quote misuse through the public checkout service before remote/payment side effects. */
@ExtendWith(MockitoExtension.class)
class CustomerOrderQuoteValidationTest {
    @Mock CustomerAddressRepository addresses;
    @Mock OrderQuoteRepository quotes;
    @Mock RestaurantClient restaurant;
    @Mock MapsServiceClient maps;
    @Mock PaymentGatewayOrchestrator gateway;
    @Mock OrderSagaOrchestrator saga;
    @Mock WalletCheckoutService wallet;
    @InjectMocks CustomerOrderService service;
    private final UUID customerId=UUID.randomUUID();
    private final UUID restaurantId=UUID.randomUUID();
    private final UUID addressId=UUID.randomUUID();
    private final UUID itemId=UUID.randomUUID();
    private final UUID quoteId=UUID.randomUUID();
    private OrderRequest request;
    private OrderQuote quote;

    @BeforeEach void arrangeValidAddressAndQuote() {
        CustomerAddress address=new CustomerAddress();address.setId(addressId);address.setCustomerId(customerId);
        when(addresses.findById(addressId)).thenReturn(Optional.of(address));
        OrderItemRequest item=new OrderItemRequest();item.setMenuItemId(itemId);item.setQuantity(1);
        request=OrderRequest.builder().quoteId(quoteId).customerId(customerId).restaurantId(restaurantId)
                .deliveryAddressId(addressId).paymentMethod(PaymentMethod.CARD).items(List.of(item)).build();
        quote=OrderQuote.builder().id(quoteId).customerId(customerId).restaurantId(restaurantId)
                .deliveryAddressId(addressId).expiresAt(Instant.now().plusSeconds(900))
                .items(Set.of(OrderQuoteItem.builder().menuItemId(itemId).quantity(1).build())).build();
    }
    @AfterEach void closeLocalExecutor() { service.cleanup(); }

    private void rejects(Class<? extends Throwable> type, String message) {
        if(request.getQuoteId()!=null) when(quotes.findById(quoteId)).thenReturn(Optional.ofNullable(quote));
        Throwable failure=catchThrowable(() -> service.createOrderWithPayment(request).join());
        assertThat(failure).isInstanceOf(CompletionException.class);
        assertThat(failure.getCause()).isInstanceOf(type).hasMessage(message);
        verifyNoInteractions(restaurant,maps,gateway,saga,wallet);
        verify(quotes,never()).claim(any(),any(),any());
        verify(quotes,never()).save(any());
    }
    @Test void missingQuoteCannotStartPayment() {
        request.setQuoteId(null);rejects(IllegalArgumentException.class,"A quote is required to place an order.");
    }
    @Test void unknownQuoteCannotStartPayment() {
        quote=null;rejects(IllegalArgumentException.class,"Quote not found or not valid for this order.");
    }
    @Test void anotherCustomersQuoteCannotStartPayment() {
        quote.setCustomerId(UUID.randomUUID());rejects(IllegalArgumentException.class,"Quote not found or not valid for this order.");
    }
    @Test void quoteForAnotherRestaurantCannotStartPayment() {
        quote.setRestaurantId(UUID.randomUUID());rejects(IllegalArgumentException.class,"Quote not found or not valid for this order.");
    }
    @Test void quoteForAnotherAddressCannotStartPayment() {
        quote.setDeliveryAddressId(UUID.randomUUID());rejects(IllegalArgumentException.class,"Quote not found or not valid for this order.");
    }
    @Test void changingQuotedItemCannotStartPayment() {
        request.getItems().get(0).setMenuItemId(UUID.randomUUID());
        rejects(QuoteExpiredException.class,"The order does not match the quote. Please request a new quote.");
    }
    @Test void changingQuotedQuantityCannotStartPayment() {
        request.getItems().get(0).setQuantity(2);
        rejects(QuoteExpiredException.class,"The order does not match the quote. Please request a new quote.");
    }
    @Test void consumedQuoteCannotStartAnotherPayment() {
        quote.setConsumedAt(Instant.now());quote.setConsumedOrderId(UUID.randomUUID());
        rejects(QuoteExpiredException.class,"This quote has already been used. Please request a new quote.");
    }
    @Test void alreadyExpiredFixtureCannotStartPaymentWithoutWaiting() {
        quote.setExpiresAt(Instant.now().minusSeconds(1));
        rejects(QuoteExpiredException.class,"This quote has expired. Please request a new quote.");
    }
}
