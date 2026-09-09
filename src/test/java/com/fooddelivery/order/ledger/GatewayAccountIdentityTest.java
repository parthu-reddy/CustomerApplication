package com.fooddelivery.order.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.LedgerAccounts;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.PaymentGateway;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.enums.InitiatorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The gateway receivable account was derived three different ways -- once at capture with a trailing
 * pipe, once at refund from the payment method rather than the gateway, and once in
 * {@link LedgerAccounts}. A refund therefore never reduced the account its capture had credited, and
 * the GATEWAY_VS_LEDGER reconciliation check could not balance.
 *
 * These tests pin the single derivation and the fail-fast on a missing gateway.
 */
public class GatewayAccountIdentityTest {

    private LedgerBookkeeper bookkeeper;
    private OutboxEventRepository outboxRepo;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        outboxRepo = mock(OutboxEventRepository.class);
        objectMapper = new ObjectMapper();
        bookkeeper = new LedgerBookkeeper(outboxRepo, objectMapper, mock(LedgerAccountResolver.class), mock(com.fooddelivery.customer.client.LedgerClient.class));
    }

    private Order paidOrder() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("250.00"));
        order.setPaymentMethod(PaymentMethod.CARD);
        return order;
    }

    private Refund refundFor(Order order, BigDecimal amount) {
        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setOrderId(order.getId());
        refund.setAmount(amount);
        refund.setDestination(RefundDestination.ORIGINAL_METHOD);
        refund.setInitiatedByType(InitiatorType.ADMIN);
        refund.setReasonCode("TEST");
        return refund;
    }

    /** Reads the single leg of the command the bookkeeper wrote to the outbox. */
    private LedgerLeg onlyLegOf(OutboxEventEntity saved) throws Exception {
        JsonNode node = objectMapper.readTree(saved.getPayload());
        LedgerTransactionCommand cmd = objectMapper.treeToValue(node, LedgerTransactionCommand.class);
        assertNotNull(cmd.getLegs(), "command carries no legs");
        assertFalse(cmd.getLegs().isEmpty(), "command carries no legs");
        return cmd.getLegs().get(0);
    }

    @Test
    void captureAndRefundAddressTheSameGatewayAccount() throws Exception {
        Order order = paidOrder();

        bookkeeper.bookPaymentCaptured(order, "RAZORPAY");
        ArgumentCaptor<OutboxEventEntity> capture = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepo).save(capture.capture());
        LedgerLeg captureLeg = onlyLegOf(capture.getValue());
        assertEquals(LedgerAccountType.GATEWAY_RECEIVABLE, captureLeg.getFromType());

        reset(outboxRepo);

        bookkeeper.bookRefund(order, refundFor(order, new BigDecimal("50.00")), "RAZORPAY");
        ArgumentCaptor<OutboxEventEntity> refund = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepo).save(refund.capture());
        LedgerLeg refundLeg = onlyLegOf(refund.getValue());
        assertEquals(LedgerAccountType.GATEWAY_RECEIVABLE, refundLeg.getToType());

        assertEquals(captureLeg.getFromId(), refundLeg.getToId(),
                "a refund must reduce the very account its capture credited");
    }

    @Test
    void theAccountIdIsTheOneLedgerAccountsDerives() throws Exception {
        Order order = paidOrder();
        bookkeeper.bookPaymentCaptured(order, "RAZORPAY");

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepo).save(captor.capture());

        assertEquals(LedgerAccounts.gatewayOwnerId(PaymentGateway.RAZORPAY), onlyLegOf(captor.getValue()).getFromId(),
                "the bookkeeper must not derive gateway accounts of its own");
    }

    @Test
    void gatewayNameIsCaseAndWhitespaceInsensitive() {
        assertEquals(LedgerAccounts.gatewayOwnerId("RAZORPAY"), LedgerAccounts.gatewayOwnerId(" razorpay "));
    }

    @Test
    void aMissingGatewayFailsRatherThanHashingNull() {
        Order order = paidOrder();

        // Previously this hashed the literal string "null" and every capture landed in one bogus account.
        assertThrows(IllegalStateException.class, () -> bookkeeper.bookPaymentCaptured(order, null));
        assertThrows(IllegalStateException.class, () -> bookkeeper.bookPaymentCaptured(order, "  "));
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void aMissingGatewayFailsOnARefundToTheOriginalMethod() {
        Order order = paidOrder();
        assertThrows(IllegalArgumentException.class,
                () -> bookkeeper.bookRefund(order, refundFor(order, new BigDecimal("10.00")), null));
    }

    @Test
    void walletAndCodBookNoGatewayCapture() {
        Order order = paidOrder();
        bookkeeper.bookPaymentCaptured(order, "WALLET");
        bookkeeper.bookPaymentCaptured(order, "COD");
        verify(outboxRepo, never()).save(any());
    }
}
