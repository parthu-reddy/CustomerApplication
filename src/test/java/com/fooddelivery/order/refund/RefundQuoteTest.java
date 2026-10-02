package com.fooddelivery.order.refund;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.RefundItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class RefundQuoteTest {

    @Test
    void testQuoteProratesTax() {
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        RefundItemRepository itemRepo = Mockito.mock(RefundItemRepository.class);

        RefundService service = new RefundService(null, orderRepo, null, null, null, null, itemRepo);

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        // Total items sum = 100 + 50 = 150
        order.setSgst(new BigDecimal("7.50"));
        order.setCgst(new BigDecimal("7.50"));
        order.setTotalAmount(new BigDecimal("165.00")); // Including tax
        order.setItemTotal(new BigDecimal("150.00"));

        OrderItem item1 = new OrderItem();
        item1.setId(UUID.randomUUID());
        item1.setPrice(new BigDecimal("100.00"));
        item1.setQuantity(1);

        OrderItem item2 = new OrderItem();
        item2.setId(UUID.randomUUID());
        item2.setPrice(new BigDecimal("50.00"));
        item2.setQuantity(1);
        
        order.setOrderItems(java.util.Set.of(item1, item2));
        
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(itemRepo.sumCompletedQuantity(eq(item1.getId()))).thenReturn(0);

        RefundCommand.Item itemCmd = new RefundCommand.Item();
        itemCmd.setOrderItemId(item1.getId());
        itemCmd.setQuantity(1);

        BigDecimal result = service.quote(orderId, List.of(itemCmd));

        // The item is 100 of a 150 item total, so it carries two thirds of each tax:
        // 100.00 + 5.00 SGST + 5.00 CGST = 110.00. Dropping the proration under-refunds by 10.00.
        assertEquals(new BigDecimal("110.00"), result);
    }

    /** Dividing by a zero item total would either throw or silently produce nonsense. */
    @Test
    void quoteRefusesWhenThereIsNoItemTotalToProrateAgainst() {
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        RefundItemRepository itemRepo = Mockito.mock(RefundItemRepository.class);
        RefundService service = new RefundService(null, orderRepo, null, null, null, null, itemRepo);

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setItemTotal(BigDecimal.ZERO);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.quote(orderId, List.of()));
        assertEquals("QUOTE_UNAVAILABLE", e.getMessage());
    }

    @Test
    void quoteRefusesAnItemThatHasAlreadyBeenRefunded() {
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        RefundItemRepository itemRepo = Mockito.mock(RefundItemRepository.class);
        RefundService service = new RefundService(null, orderRepo, null, null, null, null, itemRepo);

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setItemTotal(new BigDecimal("150.00"));
        order.setSgst(new BigDecimal("7.50"));
        order.setCgst(new BigDecimal("7.50"));

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setPrice(new BigDecimal("50.00"));
        item.setQuantity(2);
        order.setOrderItems(java.util.Set.of(item));

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        // Both units were refunded on an earlier ticket.
        when(itemRepo.sumCompletedQuantity(eq(item.getId()))).thenReturn(2);

        RefundCommand.Item itemCmd = new RefundCommand.Item();
        itemCmd.setOrderItemId(item.getId());
        itemCmd.setQuantity(1);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.quote(orderId, List.of(itemCmd)));
        assertEquals("ITEM_ALREADY_REFUNDED", e.getMessage());
    }

    @Test
    void quoteAllowsTheUnrefundedRemainderOfAPartlyRefundedItem() {
        IOrderRepository orderRepo = Mockito.mock(IOrderRepository.class);
        RefundItemRepository itemRepo = Mockito.mock(RefundItemRepository.class);
        RefundService service = new RefundService(null, orderRepo, null, null, null, null, itemRepo);

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setItemTotal(new BigDecimal("100.00"));
        order.setSgst(new BigDecimal("2.50"));
        order.setCgst(new BigDecimal("2.50"));

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setPrice(new BigDecimal("50.00"));
        item.setQuantity(2);
        order.setOrderItems(java.util.Set.of(item));

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(itemRepo.sumCompletedQuantity(eq(item.getId()))).thenReturn(1);

        RefundCommand.Item itemCmd = new RefundCommand.Item();
        itemCmd.setOrderItemId(item.getId());
        itemCmd.setQuantity(1);

        // 50.00 of a 100.00 item total, so half of each 2.50 tax: 50.00 + 1.25 + 1.25.
        assertEquals(new BigDecimal("52.50"), service.quote(orderId, List.of(itemCmd)));
    }
    @org.junit.jupiter.params.ParameterizedTest(name = "item quote request {0} {1} {2} ticket={3}")
    @org.junit.jupiter.params.provider.CsvSource({
        "55.00,ADMIN,CUSTOMER_TICKET,true,true",
        "110.00,ADMIN,CUSTOMER_TICKET,true,true",
        "110.01,ADMIN,CUSTOMER_TICKET,true,false",
        "55.00,ADMIN,CUSTOMER_TICKET,false,false",
        "55.00,CUSTOMER,CUSTOMER_TICKET,true,false",
        "55.00,ADMIN,ADMIN,true,false"
    })
    void anAuditedSupportDecisionMayReduceButNeverExpandTheSelectedItemQuote(
            String amount, String actor, String source, boolean hasTicket, boolean allowed) throws Exception {
        IOrderRepository orders = Mockito.mock(IOrderRepository.class);
        RefundItemRepository items = Mockito.mock(RefundItemRepository.class);
        var refunds = Mockito.mock(com.fooddelivery.order.repository.RefundRepository.class);
        var intents = Mockito.mock(com.fooddelivery.order.repository.IPaymentIntentRepository.class);
        var outbox = Mockito.mock(com.fooddelivery.common.outbox.repository.OutboxEventRepository.class);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var service = new RefundService(refunds, orders, intents, outbox,
                Mockito.mock(com.fooddelivery.order.ledger.LedgerBookkeeper.class), mapper, items);
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId); order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("150.00")); order.setItemTotal(new BigDecimal("100.00"));
        order.setSgst(new BigDecimal("5.00")); order.setCgst(new BigDecimal("5.00"));
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID()); item.setPrice(new BigDecimal("100.00")); item.setQuantity(1);
        order.setOrderItems(java.util.Set.of(item));
        var intent = com.fooddelivery.order.entity.PaymentIntent.builder()
                .id(UUID.randomUUID()).internalOrderId(orderId).amount(order.getTotalAmount())
                .status(com.fooddelivery.common.constants.PaymentIntentStatus.SUCCESS)
                .paymentMethod(com.fooddelivery.common.enums.PaymentMethod.CARD)
                .gatewayName(com.fooddelivery.common.enums.PaymentGateway.RAZORPAY).gatewayOrderId("dev-gateway-order").build();
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(intents.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));
        when(refunds.findByIdempotencyKey(Mockito.any())).thenReturn(Optional.empty());
        when(refunds.sumByOrderAndStatusIn(Mockito.any(), Mockito.any())).thenReturn(BigDecimal.ZERO);
        when(refunds.save(Mockito.any())).thenAnswer(call -> call.getArgument(0));
        var command = RefundCommand.builder().orderId(orderId).amount(new BigDecimal(amount))
                .items(List.of(new RefundCommand.Item(item.getId(), 1)))
                .initiatorType(com.fooddelivery.order.enums.InitiatorType.valueOf(actor)).initiatorId(UUID.randomUUID())
                .source(com.fooddelivery.order.enums.RefundSource.valueOf(source))
                .faultType(com.fooddelivery.order.enums.FaultType.UNKNOWN).reasonCode("ADMIN_RESOLUTION")
                .ticketId(hasTicket ? UUID.randomUUID() : null).idempotencyKey("owned-support-decision").build();
        if (!allowed) {
            var error = assertThrows(IllegalArgumentException.class, () -> service.request(command));
            assertEquals("REFUND_AMOUNT_DOES_NOT_MATCH_QUOTE", error.getMessage());
            Mockito.verify(refunds, Mockito.never()).save(Mockito.any());
            Mockito.verifyNoInteractions(outbox);
            return;
        }
        var view = service.request(command);
        assertEquals(0, new BigDecimal(amount).compareTo(view.getAmount()));
        var saved = org.mockito.ArgumentCaptor.forClass(com.fooddelivery.order.entity.Refund.class);
        Mockito.verify(refunds).save(saved.capture());
        assertEquals(command.getTicketId(), saved.getValue().getTicketId());
        assertEquals(item.getId(), saved.getValue().getRefundItems().iterator().next().getOrderItemId());
        var events = org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.outbox.entity.OutboxEventEntity.class);
        Mockito.verify(outbox, Mockito.atLeastOnce()).save(events.capture());
        var payment = events.getAllValues().stream().filter(e -> e.getEventType() ==
                com.fooddelivery.common.constants.EventType.PAYMENT_REFUND_REQUESTED).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal(amount).compareTo(mapper.readTree(payment.getPayload()).get("amount").decimalValue()));
    }

}
