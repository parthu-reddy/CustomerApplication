package com.fooddelivery.order.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.order.enums.InitiatorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Two partial refunds on one order must claw back the payee's share once in total, not once each.
 *
 * <p>The cap read the statement and matched {@code line.accountId} against the restaurant id. A
 * statement line's accountId is the ledger account's own surrogate key, so the filter matched
 * nothing, prior clawbacks always summed to zero, and the cap never applied. The fixture that
 * "covered" this set accountId to the restaurant id, which hid the mismatch.
 */
public class ClawbackCapTest {

    private OutboxEventRepository outboxRepo;
    private LedgerClient ledgerClient;
    private LedgerBookkeeper bookkeeper;

    private final UUID orderId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outboxRepo = Mockito.mock(OutboxEventRepository.class);
        ledgerClient = Mockito.mock(LedgerClient.class);
        bookkeeper = new LedgerBookkeeper(outboxRepo, new ObjectMapper(),
                Mockito.mock(LedgerAccountResolver.class), ledgerClient);
    }

    private Order deliveredOrder() {
        Order order = new Order();
        order.setId(orderId);
        order.setTotalAmount(new BigDecimal("200.00"));
        order.setRestaurantId(restaurantId);
        order.setRestaurantPayout(new BigDecimal("200.00"));
        order.setSgst(BigDecimal.ZERO);
        order.setCgst(BigDecimal.ZERO);
        order.setDeliveredAt(java.time.LocalDateTime.now());
        return order;
    }

    private Refund refund(String amount) {
        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setAmount(new BigDecimal(amount));
        refund.setFaultType(FaultType.RESTAURANT_FAULT);
        refund.setInitiatedByType(InitiatorType.CUSTOMER);
        refund.setDestination(RefundDestination.STORE_CREDIT);
        return refund;
    }

    /** A prior clawback line as the ledger really returns it: keyed by owner, not by account id. */
    private LedgerStatementLineDto priorClawback(String amount) {
        return LedgerStatementLineDto.builder()
                .accountId(UUID.randomUUID())   // the ledger account's surrogate key, not the payee
                .ownerId(restaurantId)
                .ownerType(LedgerAccountType.RESTAURANT_PAYABLE)
                .category(ChargeCategory.CLAWBACK)
                .direction(TransactionDirection.DEBIT)
                .amount(new BigDecimal(amount))
                .build();
    }

    private BigDecimal clawbackBookedFor(Refund refund) {
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepo).save(captor.capture());
        try {
            LedgerTransactionCommand cmd = new ObjectMapper()
                    .readValue(captor.getValue().getPayload(), LedgerTransactionCommand.class);
            return cmd.getLegs().stream()
                    .filter(l -> l.getCategory() == ChargeCategory.CLAWBACK)
                    .map(LedgerLeg::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void firstRefundClawsBackItsProRataShare() {
        when(ledgerClient.getStatementByReference(eq(orderId))).thenReturn(List.of());

        Refund first = refund("100.00");
        bookkeeper.bookRefund(deliveredOrder(), first, "RAZORPAY");

        // 50% of a 200.00 order, so 50% of the 200.00 payout.
        assertEquals(0, new BigDecimal("100.00").compareTo(clawbackBookedFor(first)));
    }

    @Test
    void secondRefundIsCappedByWhatWasAlreadyClawedBack() {
        when(ledgerClient.getStatementByReference(eq(orderId)))
                .thenReturn(List.of(priorClawback("100.00"), priorClawback("60.00")));

        Refund second = refund("100.00");
        bookkeeper.bookRefund(deliveredOrder(), second, "RAZORPAY");

        // Pro rata would be 100.00, but only 40.00 of the 200.00 payout is left to take.
        assertEquals(0, new BigDecimal("40.00").compareTo(clawbackBookedFor(second)),
                "the payee must not be charged more than they were ever paid for this order");
    }

    @Test
    void nothingIsClawedBackOnceThePayoutIsFullyRecovered() {
        when(ledgerClient.getStatementByReference(eq(orderId)))
                .thenReturn(List.of(priorClawback("200.00")));

        bookkeeper.bookRefund(deliveredOrder(), refund("100.00"), "RAZORPAY");

        // No clawback leg is left, and the refund itself is store credit, so nothing is booked.
        Mockito.verify(outboxRepo, Mockito.never()).save(any());
    }

    /** Another party's clawback on the same order must not count against this payee's cap. */
    @Test
    void anotherPayeesClawbackDoesNotCountAgainstThisOne() {
        LedgerStatementLineDto ridersClawback = LedgerStatementLineDto.builder()
                .accountId(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .ownerType(LedgerAccountType.DRIVER_PAYABLE)
                .category(ChargeCategory.CLAWBACK)
                .direction(TransactionDirection.DEBIT)
                .amount(new BigDecimal("150.00"))
                .build();
        when(ledgerClient.getStatementByReference(eq(orderId))).thenReturn(List.of(ridersClawback));

        Refund refund = refund("100.00");
        bookkeeper.bookRefund(deliveredOrder(), refund, "RAZORPAY");

        assertEquals(0, new BigDecimal("100.00").compareTo(clawbackBookedFor(refund)));
    }

    /**
     * An unreachable ledger used to yield zero prior clawbacks, which is indistinguishable from
     * "nothing clawed back yet" and lets the cap be exceeded. Refusing is recoverable; over-charging
     * a restaurant is not.
     */
    @Test
    void anUnreachableLedgerRefusesRatherThanUncappingTheClawback() {
        when(ledgerClient.getStatementByReference(eq(orderId)))
                .thenThrow(new IllegalStateException("Ledger service is unavailable"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> bookkeeper.bookRefund(deliveredOrder(), refund("100.00"), "RAZORPAY"));

        assertTrue(e.getMessage().contains("Cannot cap the clawback"), e.getMessage());
        Mockito.verify(outboxRepo, Mockito.never()).save(any());
    }
}
