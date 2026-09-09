package com.fooddelivery.order.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.client.WalletInternalClient;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.enums.InitiatorType;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.repository.RefundItemRepository;
import com.fooddelivery.order.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The destination matrix, exhaustively.
 *
 * <p>This is the table that decides whether a customer gets their money back on the card they paid
 * with, as store credit, or not at all. Every row is here, plus the two rejections and the rules on
 * the one permitted override -- because six system call sites used to pass their own destination and
 * skip the table entirely.
 */
public class RefundDestinationMatrixTest {

    private RefundRepository refundRepo;
    private IOrderRepository orderRepo;
    private IPaymentIntentRepository intentRepo;
    private OutboxEventRepository outboxRepo;
    private LedgerBookkeeper bookkeeper;
    private WalletInternalClient walletClient;
    private RefundService service;

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        refundRepo = Mockito.mock(RefundRepository.class);
        orderRepo = Mockito.mock(IOrderRepository.class);
        intentRepo = Mockito.mock(IPaymentIntentRepository.class);
        outboxRepo = Mockito.mock(OutboxEventRepository.class);
        bookkeeper = Mockito.mock(LedgerBookkeeper.class);
        walletClient = Mockito.mock(WalletInternalClient.class);
        RefundItemRepository refundItemRepo = Mockito.mock(RefundItemRepository.class);

        service = new RefundService(refundRepo, orderRepo, intentRepo, outboxRepo, bookkeeper,
                new ObjectMapper(), walletClient, refundItemRepo);

        when(refundRepo.sumByOrderAndStatusIn(any(), any())).thenReturn(BigDecimal.ZERO);
        when(refundRepo.save(any(Refund.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void order(PaymentMethod method, PaymentIntentStatus status) {
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPaymentMethod(method);

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setAmount(new BigDecimal("100.00"));
        intent.setPaymentMethod(method);
        intent.setStatus(status);
        intent.setGatewayOrderId("gw_1");
        if (method == PaymentMethod.CARD || method == PaymentMethod.UPI) {
            intent.setGatewayName(com.fooddelivery.common.enums.PaymentGateway.RAZORPAY);
        }

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(intentRepo.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));
        when(intentRepo.findById(intent.getId())).thenReturn(Optional.of(intent));
    }

    private RefundCommand command(RefundDestination override, InitiatorType initiator) {
        RefundCommand command = new RefundCommand();
        command.setOrderId(orderId);
        command.setAmount(new BigDecimal("50.00"));
        command.setDestination(override);
        command.setInitiatorType(initiator);
        command.setInitiatorId(UUID.randomUUID());
        command.setIdempotencyKey("k-" + UUID.randomUUID());
        return command;
    }

    private RefundDestination route(PaymentMethod method, PaymentIntentStatus status) {
        order(method, status);
        return service.request(command(null, InitiatorType.SYSTEM)).getDestination();
    }

    private IllegalStateException refuse(PaymentMethod method, PaymentIntentStatus status) {
        order(method, status);
        return assertThrows(IllegalStateException.class,
                () -> service.request(command(null, InitiatorType.SYSTEM)));
    }

    @Test
    void cardCaptureRefundsToTheOriginalMethod() {
        assertEquals(RefundDestination.ORIGINAL_METHOD, route(PaymentMethod.CARD, PaymentIntentStatus.SUCCESS));
        assertEquals(RefundDestination.ORIGINAL_METHOD,
                route(PaymentMethod.CARD, PaymentIntentStatus.PARTIALLY_REFUNDED));
    }

    @Test
    void upiCaptureRefundsToTheOriginalMethod() {
        assertEquals(RefundDestination.ORIGINAL_METHOD, route(PaymentMethod.UPI, PaymentIntentStatus.SUCCESS));
    }

    @Test
    void walletPaymentRefundsToStoreCredit() {
        assertEquals(RefundDestination.STORE_CREDIT, route(PaymentMethod.WALLET, PaymentIntentStatus.SUCCESS));
    }

    /** Nothing was collected, so there is nothing to give back. */
    @Test
    void uncollectedCodRefundsNothing() {
        assertEquals(RefundDestination.NONE, route(PaymentMethod.COD, PaymentIntentStatus.PENDING_COLLECTION));
        verify(walletClient, never()).credit(any(), any(), any(), any());
    }

    @Test
    void collectedCodRefundsToStoreCredit() {
        assertEquals(RefundDestination.STORE_CREDIT, route(PaymentMethod.COD, PaymentIntentStatus.COLLECTED));
    }

    /** A gateway that reported failure took no money, so there is none to give back. */
    @Test
    void aFailedPaymentRefundsNothing() {
        assertEquals(RefundDestination.NONE, route(PaymentMethod.CARD, PaymentIntentStatus.FAILED));
        assertEquals(RefundDestination.NONE, route(PaymentMethod.WALLET, PaymentIntentStatus.FAILED));
    }

    /**
     * INITIATED is deliberately refused rather than routed to NONE: we asked the gateway for money
     * and do not know whether it took any, so refunding nothing could strand a real capture.
     */
    @Test
    void cardInAnyOtherStateIsRefused() {
        assertEquals("REFUND_STATE_INVALID", refuse(PaymentMethod.CARD, PaymentIntentStatus.INITIATED).getMessage());
        assertEquals("REFUND_STATE_INVALID", refuse(PaymentMethod.CARD, PaymentIntentStatus.REFUNDED).getMessage());
    }

    @Test
    void codInAnyOtherStateIsRefused() {
        assertEquals("REFUND_STATE_INVALID", refuse(PaymentMethod.COD, PaymentIntentStatus.INITIATED).getMessage());
    }

    /**
     * The one permitted override: an administrator granting store credit as goodwill, recorded
     * against them.
     */
    @Test
    void adminMayOverrideToStoreCredit() {
        order(PaymentMethod.CARD, PaymentIntentStatus.SUCCESS);
        RefundView view = service.request(command(RefundDestination.STORE_CREDIT, InitiatorType.ADMIN));
        assertEquals(RefundDestination.STORE_CREDIT, view.getDestination());
    }

    /**
     * A wallet-paid order forced to ORIGINAL_METHOD is pushed at a gateway that never took the money:
     * the intent has no gateway, the refund sits in PROCESSING and the customer is never repaid. Six
     * system call sites did exactly this.
     */
    @Test
    void overrideToOriginalMethodIsRefused() {
        order(PaymentMethod.WALLET, PaymentIntentStatus.SUCCESS);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.request(command(RefundDestination.ORIGINAL_METHOD, InitiatorType.ADMIN)));
        assertEquals("REFUND_OVERRIDE_INVALID", e.getMessage());
        verify(outboxRepo, never()).save(any());
    }

    /**
     * The chat flow forced STORE_CREDIT on behalf of the customer, handing a card payer credit
     * instead of their money back. Only an administrator may make that choice.
     */
    @Test
    void nonAdminMayNotOverrideTheDestination() {
        order(PaymentMethod.CARD, PaymentIntentStatus.SUCCESS);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.request(command(RefundDestination.STORE_CREDIT, InitiatorType.CUSTOMER)));
        assertEquals("REFUND_OVERRIDE_UNAUTHORIZED", e.getMessage());
    }

    @Test
    void anIntentWithNoPaymentMethodIsRefused() {
        order(null, PaymentIntentStatus.SUCCESS);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.request(command(null, InitiatorType.SYSTEM)));
        assertEquals("REFUND_METHOD_UNKNOWN", e.getMessage());
    }
}
