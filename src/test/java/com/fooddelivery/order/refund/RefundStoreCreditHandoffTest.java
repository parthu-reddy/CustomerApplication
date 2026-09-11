package com.fooddelivery.order.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AggregateType;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.order.enums.InitiatorType;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.repository.RefundItemRepository;
import com.fooddelivery.order.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A store-credit refund is handed off, never called inline.
 *
 * <p>{@code RefundService.request} used to call the wallet over Feign inside its own
 * {@code @Transactional} method and then mark the refund COMPLETE. A rollback after that call left
 * the money credited with no refund row; the retry generated a fresh refund id, so the wallet's
 * entity-scoped idempotency — keyed on the refund id — treated it as a different credit and applied
 * it a second time.
 */
class RefundStoreCreditHandoffTest {

    private RefundRepository refundRepository;
    private IOrderRepository orderRepository;
    private IPaymentIntentRepository intentRepository;
    private OutboxEventRepository outbox;
    private LedgerBookkeeper bookkeeper;
    private RefundService service;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        refundRepository = mock(RefundRepository.class);
        orderRepository = mock(IOrderRepository.class);
        intentRepository = mock(IPaymentIntentRepository.class);
        outbox = mock(OutboxEventRepository.class);
        bookkeeper = mock(LedgerBookkeeper.class);

        service = new RefundService(refundRepository, orderRepository, intentRepository, outbox,
                bookkeeper, objectMapper, mock(RefundItemRepository.class));

        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setRestaurantId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("420.00"));
        order.setPaymentMethod(PaymentMethod.WALLET);

        PaymentIntent intent = PaymentIntent.builder()
                .id(UUID.randomUUID())
                .internalOrderId(orderId)
                .gatewayOrderId("INTERNAL_" + UUID.randomUUID())
                .amount(new BigDecimal("420.00"))
                .status(PaymentIntentStatus.SUCCESS)
                .paymentMethod(PaymentMethod.WALLET)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(intentRepository.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));
        when(refundRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(refundRepository.sumByOrderAndStatusIn(any(), any())).thenReturn(BigDecimal.ZERO);
        when(refundRepository.save(any(Refund.class))).thenAnswer(i -> i.getArgument(0));
    }

    private RefundView requestFullRefund() {
        return service.request(RefundCommand.builder()
                .orderId(orderId)
                .amount(new BigDecimal("420.00"))
                .faultType(FaultType.UNKNOWN)
                .initiatorType(InitiatorType.SYSTEM)
                .reasonCode("TEST")
                .idempotencyKey("test_" + orderId)
                .build());
    }

    private OutboxEventEntity eventOfType(EventType type) {
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outbox, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(e -> e.getEventType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " outbox event was written"));
    }

    @Test
    void aWalletRefundIsEnqueuedRatherThanCredited() {
        RefundView view = requestFullRefund();

        assertEquals(RefundDestination.STORE_CREDIT, view.getDestination());
        assertEquals(RefundStatus.PROCESSING, view.getStatus(),
                "the refund must stay PROCESSING until WalletService reports the credit, exactly as "
                        + "the gateway path waits for PAYMENT_REFUNDED");
        assertNotNull(eventOfType(EventType.WALLET_CREDIT_REQUESTED));
    }

    @Test
    void theRefundIsNotBookedToTheLedgerBeforeTheCreditHappens() {
        requestFullRefund();

        verify(bookkeeper, never()).bookRefund(any(), any(), any());
    }

    @Test
    void theHandoffCarriesEverythingTheCompletionPathNeeds() throws Exception {
        requestFullRefund();

        JsonNode payload = objectMapper.readTree(eventOfType(EventType.WALLET_CREDIT_REQUESTED).getPayload());
        assertEquals(customerId.toString(), payload.path("customerId").asText());
        assertEquals(orderId.toString(), payload.path("orderId").asText());
        assertEquals("420.00", payload.path("amount").asText());
        assertFalse(payload.path("refundId").asText().isBlank());
        // PaymentEventConsumer discards any payment event without a gatewayOrderId, so the
        // completion cannot be routed without this field being carried through.
        assertTrue(payload.path("gatewayOrderId").asText().startsWith("INTERNAL_"));
    }

    @Test
    void theHandoffIsKeyedOnTheRefundSoARedeliveryCannotCreditTwice() {
        requestFullRefund();

        OutboxEventEntity event = eventOfType(EventType.WALLET_CREDIT_REQUESTED);
        assertEquals(AggregateType.WALLET, event.getAggregateType());
        assertNotNull(event.getIdempotencyKey(),
                "without an idempotency key the unique constraint on outbox_events enforces nothing");
        assertTrue(event.getIdempotencyKey().startsWith("wallet_credit:"), event.getIdempotencyKey());
    }

    @Test
    void refundServiceHoldsNoWalletClientAtAll() {
        // Behaviour above proves no call is made on this path. This proves the capability is gone,
        // so the next branch cannot reintroduce it.
        boolean hasWalletField = java.util.Arrays.stream(RefundService.class.getDeclaredFields())
                .anyMatch(f -> f.getType().getSimpleName().contains("Wallet"));
        assertFalse(hasWalletField, "RefundService must not be able to call the wallet directly");
    }
}
