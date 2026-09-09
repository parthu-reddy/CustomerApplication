package com.fooddelivery.order.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AggregateType;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.order.enums.InitiatorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LedgerBookkeeperRefundTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private LedgerAccountResolver accountResolver;
    @Mock
    private LedgerClient ledgerClient;

    @InjectMocks
    private LedgerBookkeeper bookkeeper;

    @Test
    public void testRefund() throws Exception {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setPaymentMethod(PaymentMethod.CARD);
        order.setTotalAmount(new BigDecimal("100.00"));

        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setAmount(new BigDecimal("100.00"));
        refund.setDestination(RefundDestination.ORIGINAL_METHOD);
        refund.setInitiatedByType(InitiatorType.CUSTOMER);
        refund.setFaultType(FaultType.RESTAURANT_FAULT);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        bookkeeper.bookRefund(order, refund, "RAZORPAY");

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertEquals(AggregateType.LEDGER, saved.getAggregateType());
        assertEquals(EventType.LEDGER_TRANSACTION_REQUEST, saved.getEventType());
    }

    /**
     * Store credit is settled by the wallet, which books its own leg. A gateway leg here would
     * credit a gateway that is not owed anything.
     */
    @Test
    public void storeCreditBooksNoGatewayLeg() throws Exception {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setPaymentMethod(PaymentMethod.WALLET);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRestaurantId(UUID.randomUUID());
        order.setRestaurantPayout(new BigDecimal("80.00"));
        order.setDeliveredAt(java.time.LocalDateTime.now());

        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setAmount(new BigDecimal("100.00"));
        refund.setDestination(RefundDestination.STORE_CREDIT);
        refund.setInitiatedByType(InitiatorType.CUSTOMER);
        refund.setFaultType(FaultType.RESTAURANT_FAULT);

        when(ledgerClient.getStatementByReference(any())).thenReturn(java.util.List.of());
        when(objectMapper.writeValueAsString(any())).thenAnswer(i -> {
            LedgerTransactionCommand cmd = (LedgerTransactionCommand) i.getArgument(0);
            assertEquals(1, cmd.getLegs().size(), "only the clawback should be booked");
            assertEquals(com.fooddelivery.common.enums.ChargeCategory.CLAWBACK, cmd.getLegs().get(0).getCategory());
            return "{}";
        });

        bookkeeper.bookRefund(order, refund, "RAZORPAY");

        verify(outboxEventRepository).save(any());
    }

    /** An order that never reached the customer has no payout to claw back. */
    @Test
    public void anUndeliveredOrderBooksNoClawback() throws Exception {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setPaymentMethod(PaymentMethod.CARD);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRestaurantId(UUID.randomUUID());
        order.setRestaurantPayout(new BigDecimal("80.00"));
        // deliveredAt stays null

        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setAmount(new BigDecimal("100.00"));
        refund.setDestination(RefundDestination.ORIGINAL_METHOD);
        refund.setInitiatedByType(InitiatorType.SYSTEM);
        refund.setFaultType(FaultType.RESTAURANT_FAULT);

        when(objectMapper.writeValueAsString(any())).thenAnswer(i -> {
            LedgerTransactionCommand cmd = (LedgerTransactionCommand) i.getArgument(0);
            assertEquals(1, cmd.getLegs().size(), "only the gateway refund should be booked");
            assertEquals(com.fooddelivery.common.enums.ChargeCategory.REFUND, cmd.getLegs().get(0).getCategory());
            return "{}";
        });

        bookkeeper.bookRefund(order, refund, "RAZORPAY");

        verify(outboxEventRepository).save(any());
    }

    /** A refund with nothing to book must not write an empty ledger command. */
    @Test
    public void aNoneDestinationOnAnUndeliveredOrderBooksNothing() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setPaymentMethod(PaymentMethod.COD);
        order.setTotalAmount(new BigDecimal("100.00"));

        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setAmount(new BigDecimal("100.00"));
        refund.setDestination(RefundDestination.NONE);
        refund.setInitiatedByType(InitiatorType.SYSTEM);
        refund.setFaultType(FaultType.UNKNOWN);

        bookkeeper.bookRefund(order, refund, null);

        verify(outboxEventRepository, org.mockito.Mockito.never()).save(any());
    }
}
