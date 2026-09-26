package com.fooddelivery.order.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.customer.service.RiderTip;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderCharge;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.order.enums.InitiatorType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The rider's tip reaches the rider whole on delivery, and comes back with a rider-fault refund. */
class RiderTipLedgerTest {

    private final OutboxEventRepository outbox = Mockito.mock(OutboxEventRepository.class);
    private final LedgerClient ledgerClient = Mockito.mock(LedgerClient.class);
    private final LedgerBookkeeper bookkeeper = new LedgerBookkeeper(outbox, new ObjectMapper(), new LedgerAccountResolver(), ledgerClient);
    private final UUID riderId = UUID.randomUUID();

    private Order deliveredWithTip(String tip) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID());
        order.setDeliveryExecutiveId(riderId);
        order.setTotalAmount(new BigDecimal("120.00").add(new BigDecimal(tip)));
        order.setDriverNetPayout(new BigDecimal("40.00"));
        order.setRestaurantPayout(new BigDecimal("80.00"));
        order.setTipAmount(new BigDecimal(tip));
        order.setDeliveredAt(java.time.Instant.now());
        Set<OrderCharge> charges = new HashSet<>();
        OrderCharge fee = OrderCharge.builder().id(UUID.randomUUID()).category(ChargeCategory.DELIVERY_FEE)
                .payerType(com.fooddelivery.order.enums.ChargeEntityType.CUSTOMER)
                .payeeType(com.fooddelivery.order.enums.ChargeEntityType.DRIVER).amount(new BigDecimal("40.00")).build();
        charges.add(fee);
        OrderCharge tipCharge = RiderTip.charge(new BigDecimal(tip));
        if (tipCharge != null) charges.add(tipCharge);
        for (OrderCharge c : charges) c.setOrder(order);
        order.setCharges(charges);
        return order;
    }

    private LedgerTransactionCommand saved() throws Exception {
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outbox).save(captor.capture());
        return new ObjectMapper().readValue(captor.getValue().getPayload(), LedgerTransactionCommand.class);
    }

    @Test
    void theTipIsItsOwnLegToTheRider_inFull() throws Exception {
        bookkeeper.bookDelivered(deliveredWithTip("20.00"));
        List<LedgerLeg> tips = saved().getLegs().stream().filter(l -> RiderTip.DESCRIPTION.equals(l.getDescription())).toList();
        assertEquals(1, tips.size());
        assertEquals(0, new BigDecimal("20.00").compareTo(tips.get(0).getAmount()));
        assertEquals(LedgerAccountType.DRIVER_PAYABLE, tips.get(0).getToType());
        assertEquals(riderId, tips.get(0).getToId());
    }

    @Test
    void aFullRefundForTheRidersFaultClawsBackTheTipToo() throws Exception {
        when(ledgerClient.getStatementByReference(any())).thenReturn(List.of());
        Order order = deliveredWithTip("20.00");
        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setAmount(order.getTotalAmount());
        refund.setFaultType(FaultType.RIDER_FAULT);
        refund.setInitiatedByType(InitiatorType.CUSTOMER);
        refund.setDestination(RefundDestination.STORE_CREDIT);

        bookkeeper.bookRefund(order, refund, "RAZORPAY");

        BigDecimal clawed = saved().getLegs().stream()
                .filter(l -> l.getCategory() == ChargeCategory.CLAWBACK && l.getFromType() == LedgerAccountType.DRIVER_PAYABLE)
                .map(LedgerLeg::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("60.00").compareTo(clawed), "fee 40 + tip 20");
    }

    @Test
    void tipRules() {
        assertEquals(0, BigDecimal.ZERO.compareTo(RiderTip.of(null)));
        assertEquals(new BigDecimal("30.00"), RiderTip.of(new BigDecimal("30")));
        assertThrows(IllegalArgumentException.class, () -> RiderTip.of(new BigDecimal("-10")));
        assertThrows(IllegalArgumentException.class, () -> RiderTip.of(new BigDecimal("501")));
        assertThrows(IllegalArgumentException.class, () -> RiderTip.of(new BigDecimal("10.50")));
        assertNull(RiderTip.charge(BigDecimal.ZERO));
    }
}
