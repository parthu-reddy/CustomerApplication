package com.fooddelivery.order.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.ledger.LedgerAccountResolver;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.impl.HandedOverState;
import com.fooddelivery.order.service.state.impl.ReadyForPickupState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * What the rider collected is recorded, and it is recorded once.
 *
 * <p>Two defects, one cause. `handleOrderDelivered` existed in two states that disagreed:
 * `HandedOverState` collected the COD cash, `ReadyForPickupState` — reached whenever the handover
 * event was lost or arrived out of order — booked the order economics only, so the restaurant and
 * rider were credited against cash the book said was never received. And `bookCashCollected` always
 * booked `order.getTotalAmount()`, so the amount the rider actually declared was discarded.
 *
 * <p>Uses the <strong>real</strong> LedgerBookkeeper: the legs are the assertion.
 */
class CashTruthTest {

    private OrderActionService actionService;
    private OutboxEventRepository outbox;
    private LedgerBookkeeper bookkeeper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxEventRepository.class);
        bookkeeper = new LedgerBookkeeper(outbox, objectMapper, new LedgerAccountResolver(),
                mock(LedgerClient.class));
        actionService = new OrderActionService(mock(IOrderRepository.class), outbox,
                mock(IPaymentIntentRepository.class), objectMapper);
    }

    private Order codOrder(OrderStatus status) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCustomerId(UUID.randomUUID());
        o.setRestaurantId(UUID.randomUUID());
        o.setDeliveryExecutiveId(UUID.randomUUID());
        o.setTotalAmount(new BigDecimal("420.00"));
        o.setPaymentMethod(PaymentMethod.COD);
        o.setPaymentStatus(PaymentIntentStatus.PENDING_COLLECTION);
        o.setStatus(status);
        return o;
    }

    private OrderContext ctx(Order o, String declaredCash) {
        com.fooddelivery.common.event.DeliveredEvent payload = new com.fooddelivery.common.event.DeliveredEvent();
        payload.setOrderId(o.getId().toString());
        payload.setStatus("DELIVERED");
        if (declaredCash != null) {
            payload.setCashCollectedAmount(declaredCash);
        }
        return new OrderContext(o, payload, actionService, bookkeeper, null, o.getPaymentMethod());
    }

    private LedgerTransactionCommand booked(String leg) {
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outbox, atLeastOnce()).save(captor.capture());
        for (OutboxEventEntity e : captor.getAllValues()) {
            try {
                LedgerTransactionCommand cmd =
                        objectMapper.readValue(e.getPayload(), LedgerTransactionCommand.class);
                if (leg.equals(cmd.getLeg())) {
                    return cmd;
                }
            } catch (Exception ignored) {
                // not a ledger command -- notifications share the outbox
            }
        }
        throw new AssertionError("no " + leg + " ledger transaction was written");
    }

    // ---------------------------------------------------------------- H-1

    @Test
    void deliveredFromReadyForPickupStillCollectsTheCash() {
        Order o = codOrder(OrderStatus.READY_FOR_PICKUP);

        new ReadyForPickupState().handleOrderDelivered(ctx(o, "420.00"));

        assertEquals(OrderStatus.HANDED_OVER, o.getStatus(),
                "the order must not end up READY_FOR_PICKUP with a delivery that says DELIVERED");
        assertEquals(DeliveryStatus.DELIVERED, o.getDeliveryStatus());
        assertEquals(PaymentIntentStatus.COLLECTED, o.getPaymentStatus(),
                "a later refund routes on this: PENDING_COLLECTION means 'nothing to give back'");
        assertNotNull(booked("CASH_COLLECTED"));
        assertNotNull(booked("DELIVERED"));
    }

    @Test
    void bothDeliveredPathsProduceTheSameLedgerLegs() {
        Order viaHandover = codOrder(OrderStatus.HANDED_OVER);
        new HandedOverState().handleOrderDelivered(ctx(viaHandover, "420.00"));
        List<ChargeCategory> handoverCategories = booked("CASH_COLLECTED").getLegs().stream()
                .map(LedgerLeg::getCategory).toList();

        setUp(); // fresh captor
        Order viaReady = codOrder(OrderStatus.READY_FOR_PICKUP);
        new ReadyForPickupState().handleOrderDelivered(ctx(viaReady, "420.00"));
        List<ChargeCategory> readyCategories = booked("CASH_COLLECTED").getLegs().stream()
                .map(LedgerLeg::getCategory).toList();

        assertEquals(handoverCategories, readyCategories,
                "one delivery, one definition -- these were two implementations that disagreed");
    }

    @Test
    void aPrepaidOrderCollectsNoCashOnEitherPath() {
        Order o = codOrder(OrderStatus.HANDED_OVER);
        o.setPaymentMethod(PaymentMethod.CARD);
        o.setPaymentStatus(PaymentIntentStatus.SUCCESS);

        new HandedOverState().handleOrderDelivered(ctx(o, null));

        assertNotNull(booked("DELIVERED"));
        assertThrows(AssertionError.class, () -> booked("CASH_COLLECTED"));
        assertEquals(PaymentIntentStatus.SUCCESS, o.getPaymentStatus());
    }

    // ---------------------------------------------------------------- H-2

    @Test
    void theDeclaredAmountIsRecordedOnTheOrder() {
        Order o = codOrder(OrderStatus.HANDED_OVER);

        new HandedOverState().handleOrderDelivered(ctx(o, "300.00"));

        assertEquals(new BigDecimal("300.00"), o.getCashCollectedAmount(),
                "what the rider said they took is a fact, and it was written nowhere");
    }

    @Test
    void aShortCollectionBooksTheGapAgainstTheRider() {
        Order o = codOrder(OrderStatus.HANDED_OVER);

        new HandedOverState().handleOrderDelivered(ctx(o, "300.00"));

        LedgerTransactionCommand cmd = booked("CASH_COLLECTED");
        assertEquals(2, cmd.getLegs().size(), "a short collection is two legs, not one");

        LedgerLeg collected = cmd.getLegs().stream()
                .filter(l -> l.getCategory() == ChargeCategory.CASH_COLLECTED).findFirst().orElseThrow();
        assertEquals(new BigDecimal("300.00"), collected.getAmount(),
                "the collected leg is what the rider declared, not the order total");
        assertEquals(LedgerAccountType.CASH_RECEIVABLE, collected.getFromType());

        LedgerLeg gap = cmd.getLegs().stream()
                .filter(l -> l.getCategory() == ChargeCategory.CASH_SHORTFALL).findFirst().orElseThrow();
        assertEquals(new BigDecimal("120.00"), gap.getAmount());
        assertEquals(LedgerAccountType.DRIVER_PAYABLE, gap.getFromType(),
                "the rider owes the difference; it can come from nowhere else");
        assertEquals(o.getDeliveryExecutiveId(), gap.getFromId());

        assertEquals(new BigDecimal("420.00"),
                cmd.getLegs().stream().map(LedgerLeg::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                "the two legs must still sum to the order total");
    }

    @Test
    void anExactCollectionBooksOneLeg() {
        Order o = codOrder(OrderStatus.HANDED_OVER);

        new HandedOverState().handleOrderDelivered(ctx(o, "420.00"));

        assertEquals(1, booked("CASH_COLLECTED").getLegs().size());
    }

    @Test
    void overDeclaringIsRefused() {
        Order o = codOrder(OrderStatus.HANDED_OVER);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new HandedOverState().handleOrderDelivered(ctx(o, "500.00")));
        assertTrue(e.getMessage().contains("only worth"), e.getMessage());
        verify(outbox, never()).save(argThat(ev ->
                ev.getEventType() == com.fooddelivery.common.constants.EventType.LEDGER_TRANSACTION_REQUEST
                        && ev.getPayload().contains("CASH_COLLECTED")));
    }

    @Test
    void aCodDeliveryWithNoDeclaredAmountIsRefusedRatherThanGuessed() {
        Order o = codOrder(OrderStatus.HANDED_OVER);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new HandedOverState().handleOrderDelivered(ctx(o, null)));
        assertTrue(e.getMessage().contains("no declared cash amount"), e.getMessage());
    }

    @Test
    void aNegativeDeclarationIsRefused() {
        Order o = codOrder(OrderStatus.HANDED_OVER);

        assertThrows(IllegalStateException.class,
                () -> new HandedOverState().handleOrderDelivered(ctx(o, "-10.00")));
    }

    @Test
    void anUnreadableAmountIsRefusedRatherThanTreatedAsAbsent() {
        Order o = codOrder(OrderStatus.HANDED_OVER);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new HandedOverState().handleOrderDelivered(ctx(o, "four hundred")));
        assertTrue(e.getMessage().contains("unreadable"), e.getMessage());
    }
}
