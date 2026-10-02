package com.fooddelivery.customer.service;

import com.fooddelivery.customer.dto.OrderRequest;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.PaymentGatewayOrchestrator;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.constants.EventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Mocked orchestration proof; transaction commit/outbox atomicity needs database integration. */
@ExtendWith(MockitoExtension.class)
class CustomerOrderPaymentCompensationTest {
    @Mock IOrderRepository orders;
    @Mock PaymentGatewayOrchestrator gateway;
    @Mock WalletCheckoutService wallet;
    @Mock TransactionTemplate transactions;
    @Mock OutboxEventRepository outbox;
    @Spy @InjectMocks CustomerOrderService service;
    private OrderRequest request;
    private Order order;

    @BeforeEach void arrangeCreatedOrder() {
        order=new Order();order.setId(UUID.randomUUID());order.setCustomerId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID());order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(new java.math.BigDecimal("63.35"));
        request=OrderRequest.builder().customerId(order.getCustomerId()).restaurantId(order.getRestaurantId())
                .quoteId(UUID.randomUUID()).paymentMethod(PaymentMethod.CARD).build();
        doReturn(CompletableFuture.completedFuture(order)).when(service).createOrder(request);
    }
    @AfterEach void closeLocalExecutor() { service.cleanup(); }
    private void executeCompensationTransaction() {
        doAnswer(call -> { Consumer<TransactionStatus> work=call.getArgument(0);work.accept(null);return null; })
                .when(transactions).executeWithoutResult(any());
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
    }
    private void assertCancelledEvent() throws Exception {
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).contains("Payment intent generation failed");
        verify(transactions,times(1)).executeWithoutResult(any());verify(orders,times(1)).save(order);
        ArgumentCaptor<OutboxEventEntity> capture=ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outbox,times(1)).save(capture.capture());
        assertThat(capture.getValue().getEventType()).isEqualTo(EventType.ORDER_CANCELLED);
        assertThat(capture.getValue().getAggregateId()).isEqualTo(order.getId().toString());
        assertThat(new ObjectMapper().readTree(capture.getValue().getPayload()).get("orderId").asText())
                .isEqualTo(order.getId().toString());
    }
    @Test void failedIntentCancelsCreatedOrderAndPublishesOneCancellation() throws Exception {
        executeCompensationTransaction();
        when(gateway.generateIntent(order,PaymentMethod.CARD)).thenThrow(new IllegalStateException("declined"));
        assertThatThrownBy(() -> service.createOrderWithPayment(request).join()).isInstanceOf(CompletionException.class);
        assertCancelledEvent();verifyNoInteractions(wallet);
    }
    @Test void failedWalletSettlementCancelsCreatedOrderAndPublishesOneCancellation() throws Exception {
        request.setPaymentMethod(PaymentMethod.WALLET);executeCompensationTransaction();
        when(gateway.generateIntent(order,PaymentMethod.WALLET)).thenReturn("INTERNAL_test");
        doThrow(new IllegalStateException("insufficient balance")).when(wallet).processWallet(order,PaymentMethod.WALLET,"INTERNAL_test");
        assertThatThrownBy(() -> service.createOrderWithPayment(request).join()).isInstanceOf(CompletionException.class);
        assertCancelledEvent();verify(wallet,times(1)).processWallet(order,PaymentMethod.WALLET,"INTERNAL_test");
    }
    @Test void intentFailureDoesNotCancelAnOrderThatAlreadyProgressed() {
        order.setStatus(OrderStatus.ACCEPTED);executeCompensationTransaction();
        when(gateway.generateIntent(order,PaymentMethod.CARD)).thenThrow(new IllegalStateException("provider error"));
        assertThatThrownBy(() -> service.createOrderWithPayment(request).join()).isInstanceOf(CompletionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        verify(orders,never()).save(any());verifyNoInteractions(outbox,wallet);
    }
    @Test void successfulPaymentReturnsSameOrderAndIntentWithoutCompensation() {
        when(gateway.generateIntent(order,PaymentMethod.CARD)).thenReturn("mock_intent");
        CustomerOrderService.OrderWithPayment result=service.createOrderWithPayment(request).join();
        assertThat(result.order()).isSameAs(order);assertThat(result.paymentIntent()).isEqualTo("mock_intent");
        verify(gateway,times(1)).generateIntent(order,PaymentMethod.CARD);
        verify(wallet,times(1)).processWallet(order,PaymentMethod.CARD,"mock_intent");
        verifyNoInteractions(orders,outbox,transactions);
    }
}
