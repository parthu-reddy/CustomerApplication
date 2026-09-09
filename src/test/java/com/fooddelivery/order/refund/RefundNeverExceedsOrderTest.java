package com.fooddelivery.order.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.client.WalletInternalClient;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.PaymentGateway;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.enums.RefundStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * However many refunds are requested on an order, the accepted total never exceeds what was paid.
 *
 * <p>This class was two methods of `assertTrue(true, "<a sentence>")`. It is the third of the three
 * invariants the plan called "worth more than any number of example tests"; none of them asserted
 * anything, and gate 4.3 passed because it only checks that the classes exist.
 *
 * <p>Driven as a real sequence: each accepted refund is added to a ledger the mocked repository sums,
 * exactly as {@code sumByOrderAndStatusIn} does, so the guard is exercised against accumulating state
 * rather than a single call.
 */
class RefundNeverExceedsOrderTest {

    private RefundRepository refundRepository;
    private RefundService refundService;
    private final List<Refund> accepted = new ArrayList<>();

    private final UUID orderId = UUID.randomUUID();
    private final BigDecimal orderTotal = new BigDecimal("100.00");

    @BeforeEach
    void setUp() {
        accepted.clear();
        refundRepository = Mockito.mock(RefundRepository.class);
        IOrderRepository orderRepository = Mockito.mock(IOrderRepository.class);
        IPaymentIntentRepository intentRepository = Mockito.mock(IPaymentIntentRepository.class);

        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(orderTotal);
        order.setPaymentMethod(PaymentMethod.CARD);

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setAmount(orderTotal);
        intent.setPaymentMethod(PaymentMethod.CARD);
        intent.setStatus(PaymentIntentStatus.SUCCESS);
        intent.setGatewayOrderId("gw_1");
        intent.setGatewayName(PaymentGateway.RAZORPAY);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(intentRepository.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));
        when(intentRepository.findById(intent.getId())).thenReturn(Optional.of(intent));
        when(refundRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        // The repository sum the guard reads. It honours the status list it is given, exactly as
        // the real query does -- a mock that ignores the argument cannot notice the predicate
        // changing, and this test did not fail when REQUESTED and PROCESSING were dropped from it.
        when(refundRepository.sumByOrderAndStatusIn(any(), any())).thenAnswer(i -> {
            @SuppressWarnings("unchecked")
            List<RefundStatus> statuses = i.getArgument(1);
            return accepted.stream()
                    .filter(r -> statuses.contains(r.getStatus()))
                    .map(Refund::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        });
        when(refundRepository.save(any(Refund.class))).thenAnswer(i -> {
            Refund r = i.getArgument(0);
            accepted.add(r);
            return r;
        });

        refundService = new RefundService(refundRepository, orderRepository, intentRepository,
                Mockito.mock(OutboxEventRepository.class), Mockito.mock(LedgerBookkeeper.class),
                new ObjectMapper(), Mockito.mock(WalletInternalClient.class),
                Mockito.mock(RefundItemRepository.class));
    }

    private RefundCommand request(String amount) {
        RefundCommand cmd = new RefundCommand();
        cmd.setOrderId(orderId);
        cmd.setAmount(new BigDecimal(amount));
        cmd.setInitiatorType(InitiatorType.SYSTEM);
        cmd.setIdempotencyKey("k-" + UUID.randomUUID());
        return cmd;
    }

    private BigDecimal acceptedTotal() {
        return accepted.stream().map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Invariant: for any sequence of requests, what is accepted never exceeds the order total.
     * The last request in each sequence is the one that would breach it.
     */
    @ParameterizedTest(name = "sequence {0}")
    @CsvSource({
            "'40.00,40.00,40.00'",
            "'99.99,0.01,0.01'",
            "'100.00,0.01'",
            "'1.00,1.00,1.00,1.00,97.00,1.00'",
            "'50.00,50.00,50.00'"
    })
    void noSequenceOfRefundsCanExceedTheOrderTotal(String sequence) {
        boolean refusedAtLeastOnce = false;
        for (String amount : sequence.split(",")) {
            try {
                refundService.request(request(amount));
            } catch (IllegalStateException e) {
                assertEquals("REFUND_EXCEEDS_REMAINING", e.getMessage());
                refusedAtLeastOnce = true;
            }
            assertTrue(acceptedTotal().compareTo(orderTotal) <= 0,
                    "accepted " + acceptedTotal() + " against an order of " + orderTotal
                            + " after the sequence " + sequence);
        }
        assertTrue(refusedAtLeastOnce, "every one of these sequences must be refused at some point");
    }

    /** A refund for exactly the remaining amount is allowed; the next one is not. */
    @Test
    void theBoundaryIsInclusive() {
        refundService.request(request("60.00"));
        refundService.request(request("40.00"));

        assertEquals(0, orderTotal.compareTo(acceptedTotal()));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> refundService.request(request("0.01")));
        assertEquals("REFUND_EXCEEDS_REMAINING", e.getMessage());
    }

    /** A full refund leaves nothing, so any subsequent request is refused. */
    @Test
    void aFullRefundBlocksEverySubsequentRefund() {
        refundService.request(request("100.00"));

        for (String amount : new String[]{"0.01", "1.00", "100.00"}) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> refundService.request(request(amount)));
            assertEquals("REFUND_EXCEEDS_REMAINING", e.getMessage());
        }
        assertEquals(1, accepted.size());
    }

    /** A refund that was requested but has not completed still counts against the remainder. */
    @Test
    void moneyStillInFlightCountsAgainstTheRemainder() {
        refundService.request(request("60.00"));
        // A card refund is handed to the gateway straight away, so it sits in PROCESSING rather
        // than REQUESTED. Either way it is money in flight and must count against the remainder.
        assertTrue(accepted.get(0).getStatus() == RefundStatus.PROCESSING
                        || accepted.get(0).getStatus() == RefundStatus.REQUESTED,
                "expected an in-flight status, got " + accepted.get(0).getStatus());

        // 60 is in flight, so only 40 is left.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> refundService.request(request("41.00")));
        assertEquals("REFUND_EXCEEDS_REMAINING", e.getMessage());
    }
}
