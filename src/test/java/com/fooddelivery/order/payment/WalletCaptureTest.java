package com.fooddelivery.order.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.ledger.LedgerAccountResolver;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.impl.CreatedState;
import com.fooddelivery.order.service.state.impl.TerminalState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A wallet-paid order must reach the restaurant, and the capture must appear in the book.
 *
 * <p>These tests use the <strong>real</strong> {@link LedgerBookkeeper}, not a mock. Every other
 * state test mocks it, which is exactly why this shipped: the wallet intent is built with
 * {@code gatewayName(null)}, {@code CreatedState} sent every non-COD method into
 * {@code bookPaymentCaptured}, and that method refuses a null gateway — so every wallet order threw,
 * rolled back and was retried into the DLT, after the wallet had already been debited.
 *
 * <p>A mocked bookkeeper cannot observe that, so a mocked bookkeeper is not allowed here.
 */
class WalletCaptureTest {

    private OrderActionService actionService;
    private OutboxEventRepository outbox;
    private LedgerBookkeeper bookkeeper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        actionService = mock(OrderActionService.class);
        outbox = mock(OutboxEventRepository.class);
        objectMapper = new ObjectMapper();
        bookkeeper = new LedgerBookkeeper(outbox, objectMapper, new LedgerAccountResolver(),
                mock(LedgerClient.class));
    }

    private Order order(PaymentMethod method, OrderStatus status) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCustomerId(UUID.randomUUID());
        o.setRestaurantId(UUID.randomUUID());
        o.setTotalAmount(new BigDecimal("420.00"));
        o.setPaymentMethod(method);
        o.setStatus(status);
        return o;
    }

    private OrderContext ctx(Order o, String gateway) {
        return new OrderContext(o, objectMapper.createObjectNode(), actionService, bookkeeper,
                gateway, o.getPaymentMethod());
    }

    private LedgerTransactionCommand bookedCommand() throws Exception {
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outbox).save(captor.capture());
        return objectMapper.readValue(captor.getValue().getPayload(), LedgerTransactionCommand.class);
    }

    @Test
    void aWalletOrderReachesTheRestaurantWithNoGatewayName() {
        Order o = order(PaymentMethod.WALLET, OrderStatus.CREATED);

        // Exactly what PaymentEventConsumer passes for a wallet intent: no gateway.
        new CreatedState().handlePaymentSuccess(ctx(o, null));

        assertEquals(OrderStatus.PENDING_ACCEPTANCE, o.getStatus(),
                "a wallet order must be sent to the restaurant like any other paid order");
        assertEquals(PaymentIntentStatus.SUCCESS, o.getPaymentStatus());
        verify(actionService).updatePaymentIntentStatus(o.getId(), PaymentIntentStatus.SUCCESS);
        verify(actionService).emitOrderPaidEvent(o);
        verify(actionService, never()).emitOrderPlacedCodEvent(any());
    }

    @Test
    void theWalletCaptureIsBookedAgainstTheCustomersPrepaidAccount() throws Exception {
        Order o = order(PaymentMethod.WALLET, OrderStatus.CREATED);

        new CreatedState().handlePaymentSuccess(ctx(o, null));

        LedgerTransactionCommand cmd = bookedCommand();
        assertEquals("WALLET_CAPTURE", cmd.getLeg());
        assertEquals(1, cmd.getLegs().size());
        LedgerLeg leg = cmd.getLegs().get(0);
        assertEquals(LedgerAccountType.CUSTOMER_CREDIT, leg.getFromType(),
                "the money came out of the customer's prepaid balance, not an external receivable");
        assertEquals(o.getCustomerId(), leg.getFromId());
        assertEquals(LedgerAccountType.PLATFORM_CLEARING, leg.getToType());
        assertEquals(new BigDecimal("420.00"), leg.getAmount());
    }

    @Test
    void aCardOrderStillBooksAgainstItsGateway() throws Exception {
        Order o = order(PaymentMethod.CARD, OrderStatus.CREATED);

        new CreatedState().handlePaymentSuccess(ctx(o, "RAZORPAY"));

        LedgerTransactionCommand cmd = bookedCommand();
        assertEquals("PAYMENT_CAPTURE", cmd.getLeg());
        assertEquals(LedgerAccountType.GATEWAY_RECEIVABLE, cmd.getLegs().get(0).getFromType());
    }

    @Test
    void aCardOrderWithNoGatewayStillFailsLoudly() {
        Order o = order(PaymentMethod.CARD, OrderStatus.CREATED);

        // The blank-gateway guard is the one thing that must NOT be relaxed by this change: a card
        // capture whose gateway is unknown would otherwise land in a fabricated account.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new CreatedState().handlePaymentSuccess(ctx(o, null)));
        assertTrue(e.getMessage().contains("without a gateway name"), e.getMessage());
    }

    @Test
    void aCodOrderBooksNothingAndStaysPendingCollection() {
        Order o = order(PaymentMethod.COD, OrderStatus.CREATED);

        new CreatedState().handlePaymentSuccess(ctx(o, null));

        assertEquals(PaymentIntentStatus.PENDING_COLLECTION, o.getPaymentStatus());
        verify(actionService).emitOrderPlacedCodEvent(o);
        verifyNoInteractions(outbox);
    }

    @Test
    void anOrderWithNoPaymentMethodIsADataDefectNotAStateError() {
        Order o = order(null, OrderStatus.CREATED);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new CreatedState().handlePaymentSuccess(ctx(o, null)));
        assertTrue(e.getMessage().contains("no payment method"), e.getMessage());
        assertFalse(e instanceof com.fooddelivery.common.exception.IllegalStateTransitionException,
                "the consumers catch and swallow IllegalStateTransitionException; this must not be one");
    }

    @Test
    void aLateWalletCaptureIsBookedRatherThanThrowing() throws Exception {
        Order o = order(PaymentMethod.WALLET, OrderStatus.CANCELLED);
        o.setCancellationReason("Cancelled by customer via UI");

        new TerminalState().handlePaymentSuccess(ctx(o, null));

        LedgerTransactionCommand cmd = bookedCommand();
        assertEquals("WALLET_CAPTURE", cmd.getLeg(),
                "TerminalState routed on the gateway too, so a late wallet capture threw here as well");
    }
}
