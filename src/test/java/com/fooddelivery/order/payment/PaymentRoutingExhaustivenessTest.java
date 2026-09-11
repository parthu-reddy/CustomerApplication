package com.fooddelivery.order.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.ledger.LedgerAccountResolver;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.service.state.OrderActionService;
import com.fooddelivery.order.service.state.OrderContext;
import com.fooddelivery.order.service.state.impl.CreatedState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Every payment method the platform offers must be able to pay for an order.
 *
 * <p>Driven off {@code PaymentMethod.values()} rather than a hand-written list, so a fifth method
 * cannot be added and left with no route. The {@code switch} in {@code CreatedState} is exhaustive,
 * so a new constant is a compile error there too — this test is what catches the case where someone
 * adds the constant *and* a branch that quietly does nothing.
 */
class PaymentRoutingExhaustivenessTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String gatewayFor(PaymentMethod method) {
        // What PaymentGatewayOrchestrator actually records: CARD and UPI go to a gateway, the other
        // two are settled internally and have no gateway name at all.
        return switch (method) {
            case CARD, UPI -> "RAZORPAY";
            case WALLET, COD -> null;
        };
    }

    @Test
    void everyPaymentMethodCanPayForAnOrder() {
        for (PaymentMethod method : PaymentMethod.values()) {
            Order o = new Order();
            o.setId(UUID.randomUUID());
            o.setCustomerId(UUID.randomUUID());
            o.setRestaurantId(UUID.randomUUID());
            o.setTotalAmount(new BigDecimal("420.00"));
            o.setPaymentMethod(method);
            o.setStatus(OrderStatus.CREATED);

            LedgerBookkeeper bookkeeper = new LedgerBookkeeper(mock(OutboxEventRepository.class),
                    objectMapper, new LedgerAccountResolver(), mock(LedgerClient.class));
            OrderContext ctx = new OrderContext(o, objectMapper.createObjectNode(),
                    mock(OrderActionService.class), bookkeeper, gatewayFor(method), method);

            assertDoesNotThrow(() -> new CreatedState().handlePaymentSuccess(ctx),
                    () -> method + " cannot pay for an order");
            assertEquals(OrderStatus.PENDING_ACCEPTANCE, o.getStatus(),
                    method + " left the order in CREATED, so the restaurant never hears about it");
            assertNotNull(o.getPaymentStatus(), method + " recorded no payment state");
        }
    }

    @Test
    void theSwitchCoversEveryMethodWithNoDefaultBranch() {
        // If a default branch is ever added, an unhandled method would silently take it and this
        // test would still pass -- so assert the shape of the source, not only its behaviour.
        String src = readCreatedState();
        assertFalse(src.contains("default ->"),
                "handlePaymentSuccess must not have a default branch: an unhandled payment method has "
                        + "to be a compile error, not a silent no-op");
        for (PaymentMethod method : PaymentMethod.values()) {
            assertTrue(src.contains(method.name()), method + " is not named in CreatedState");
        }
    }

    private String readCreatedState() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/fooddelivery/order/service/state/impl/CreatedState.java"));
        } catch (java.io.IOException e) {
            throw new AssertionError("CreatedState.java is not readable from the module root", e);
        }
    }
}
