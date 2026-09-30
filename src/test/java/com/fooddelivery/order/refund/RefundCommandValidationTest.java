package com.fooddelivery.order.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.PaymentGateway;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.order.enums.InitiatorType;
import com.fooddelivery.order.enums.RefundSource;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.repository.RefundItemRepository;
import com.fooddelivery.order.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundCommandValidationTest {

    private RefundRepository refunds;
    private IOrderRepository orders;
    private IPaymentIntentRepository intents;
    private RefundItemRepository refundItems;
    private RefundService service;
    private UUID orderId;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        refunds = Mockito.mock(RefundRepository.class);
        orders = Mockito.mock(IOrderRepository.class);
        intents = Mockito.mock(IPaymentIntentRepository.class);
        refundItems = Mockito.mock(RefundItemRepository.class);
        service = new RefundService(refunds, orders, intents, Mockito.mock(OutboxEventRepository.class),
                Mockito.mock(LedgerBookkeeper.class), new ObjectMapper(), refundItems);

        orderId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(UUID.randomUUID());
        order.setPaymentMethod(PaymentMethod.CARD);
        order.setItemTotal(new BigDecimal("10.00"));
        order.setSgst(BigDecimal.ZERO);
        order.setCgst(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("10.00"));
        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setQuantity(1);
        item.setPrice(new BigDecimal("10.00"));
        order.setOrderItems(java.util.Set.of(item));

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setAmount(new BigDecimal("10.00"));
        intent.setPaymentMethod(PaymentMethod.CARD);
        intent.setStatus(PaymentIntentStatus.SUCCESS);
        intent.setGatewayName(PaymentGateway.RAZORPAY);
        intent.setGatewayOrderId("gateway-order");

        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(intents.findByInternalOrderIdForUpdate(orderId)).thenReturn(Optional.of(intent));
        when(intents.findById(intent.getId())).thenReturn(Optional.of(intent));
        when(refunds.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(refunds.sumByOrderAndStatusIn(eq(orderId), any())).thenReturn(BigDecimal.ZERO);
        when(refundItems.sumCompletedQuantity(itemId)).thenReturn(0);
    }

    @Test
    void malformedAmountsAreRejectedBeforeAnyLookupOrPersistence() {
        for (BigDecimal amount : List.of(BigDecimal.ZERO, new BigDecimal("-0.01"), new BigDecimal("1.001"))) {
            RefundCommand command = validCommand();
            command.setAmount(amount);

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> service.request(command));
            assertEquals("REFUND_AMOUNT_INVALID", error.getMessage());
        }

        RefundCommand nullAmount = validCommand();
        nullAmount.setAmount(null);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.request(nullAmount));
        assertEquals("REFUND_AMOUNT_INVALID", error.getMessage());
        verify(refunds, never()).findByIdempotencyKey(any());
        verify(refunds, never()).save(any());
    }

    @Test
    void losslesslyNormalizableMoneyIsAcceptedAndSubcentMoneyIsRejected() {
        RefundCommand normalized = validCommand();
        normalized.setAmount(new BigDecimal("9.990"));
        normalized.setItems(List.of(new RefundCommand.Item(itemId, 1)));

        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
                () -> service.request(normalized));
        assertEquals("REFUND_AMOUNT_DOES_NOT_MATCH_QUOTE", mismatch.getMessage());
    }

    @Test
    void itemRefundMustMatchTheServerQuote() {
        RefundCommand command = validCommand();
        command.setAmount(new BigDecimal("9.99"));
        command.setItems(List.of(new RefundCommand.Item(itemId, 1)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.request(command));
        assertEquals("REFUND_AMOUNT_DOES_NOT_MATCH_QUOTE", error.getMessage());
        verify(refunds, never()).save(any());
    }

    @Test
    void quoteRejectsForeignDuplicateAndNonpositiveItems() {
        UUID foreignItem = UUID.randomUUID();

        IllegalArgumentException foreign = assertThrows(IllegalArgumentException.class,
                () -> service.quote(orderId, List.of(new RefundCommand.Item(foreignItem, 1))));
        assertEquals("REFUND_ITEM_NOT_ON_ORDER", foreign.getMessage());

        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> service.quote(orderId, List.of(new RefundCommand.Item(itemId, 1),
                        new RefundCommand.Item(itemId, 1))));
        assertEquals("REFUND_ITEM_DUPLICATE", duplicate.getMessage());

        IllegalArgumentException nonpositive = assertThrows(IllegalArgumentException.class,
                () -> service.quote(orderId, List.of(new RefundCommand.Item(itemId, 0))));
        assertEquals("REFUND_ITEMS_INVALID", nonpositive.getMessage());
    }

    @Test
    void emptyItemQuoteMeansTheWholeOrderAndNeverZero() {
        assertEquals(new BigDecimal("10.00"), service.quote(orderId, List.of()));
    }

    @Test
    void requiredCommandMetadataIsControlledBeforePersistence() {
        RefundCommand command = validCommand();
        command.setIdempotencyKey(" ");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.request(command));
        assertEquals("REFUND_IDEMPOTENCY_KEY_INVALID", error.getMessage());
        verify(refunds, never()).save(any());
    }

    private RefundCommand validCommand() {
        return RefundCommand.builder()
                .orderId(orderId)
                .amount(new BigDecimal("10.00"))
                .faultType(FaultType.UNKNOWN)
                .source(RefundSource.SYSTEM_CANCELLATION)
                .initiatorType(InitiatorType.SYSTEM)
                .reasonCode("TEST")
                .idempotencyKey("refund-" + UUID.randomUUID())
                .build();
    }
}
