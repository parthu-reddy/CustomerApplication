package com.fooddelivery.order.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.PaymentGateway;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.order.enums.InitiatorType;
import com.fooddelivery.order.enums.RefundSource;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.repository.RefundRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * An automatic refund requested inside a caller's transaction (OrderEventConsumer and
 * PaymentEventConsumer move the order, then ask for its refund in the same transaction). A refund
 * that cannot be routed must not cost the caller its state change; a refund that fails after its
 * row is written must.
 *
 * <p>Real proxies, real transactions and committed H2 rows: a mocked RefundService cannot show the
 * rollback-only marking that made the consumers' {@code catch (IllegalStateException)} useless.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = {"com.fooddelivery.order.entity", "com.fooddelivery.common.outbox.entity"})
@EnableJpaRepositories(basePackages = {"com.fooddelivery.order.repository", "com.fooddelivery.common.outbox.repository"})
@Import({RefundService.class, RefundRefusalInCallerTransactionTest.Config.class})
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:refundRefusalInCaller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefundRefusalInCallerTransactionTest {

    @TestConfiguration
    static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    @Autowired RefundService service;
    @Autowired TransactionTemplate transactions;
    @Autowired IOrderRepository orders;
    @Autowired IPaymentIntentRepository intents;
    @Autowired RefundRepository refunds;
    @SpyBean OutboxEventRepository outbox;
    @MockBean LedgerBookkeeper ledger;

    /**
     * Control, and the defect as the consumers had it: catching the refusal does not save the
     * caller, because the exception already marked the shared transaction rollback-only.
     */
    @Test
    void aRefusalThrownThroughTheProxyRollsBackTheCallersStateChange() {
        Order order = acceptedCardOrder(PaymentIntentStatus.INITIATED);

        assertThrows(UnexpectedRollbackException.class, () -> transactions.executeWithoutResult(status -> {
            cancelByRestaurant(order.getId());
            try {
                service.request(automaticRefund(order));
            } catch (IllegalStateException refusal) {
                assertEquals("REFUND_STATE_INVALID", refusal.getMessage());
            }
        }));

        assertEquals(OrderStatus.ACCEPTED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void aReturnedRefusalKeepsTheCallersStateChange() {
        Order order = acceptedCardOrder(PaymentIntentStatus.INITIATED);

        Optional<String> refusal = transactions.execute(status -> {
            cancelByRestaurant(order.getId());
            return service.requestUnlessRefused(automaticRefund(order));
        });

        // INITIATED is deliberately not routed: an operator must look (RefundService.resolveDestination).
        assertEquals(Optional.of("REFUND_STATE_INVALID"), refusal);
        assertEquals(OrderStatus.CANCELLED_BY_RESTAURANT, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(List.of(), refundsOf(order));
        assertEquals(0, outboxOf(order));
    }

    @Test
    void anAcceptedRefundIsWrittenWithTheCallersStateChange() {
        Order order = acceptedCardOrder(PaymentIntentStatus.SUCCESS);

        Optional<String> refusal = transactions.execute(status -> {
            cancelByRestaurant(order.getId());
            return service.requestUnlessRefused(automaticRefund(order));
        });

        assertEquals(Optional.empty(), refusal);
        assertEquals(OrderStatus.CANCELLED_BY_RESTAURANT, orders.findById(order.getId()).orElseThrow().getStatus());
        List<Refund> written = refundsOf(order);
        assertEquals(1, written.size());
        assertEquals(RefundStatus.PROCESSING, written.get(0).getStatus());
        assertEquals(0, new BigDecimal("53.53").compareTo(written.get(0).getAmount()));
        assertTrue(outbox.findAll().stream().anyMatch(e -> e.getEventType() == EventType.PAYMENT_REFUND_REQUESTED
                && e.getPayload().contains(written.get(0).getId().toString())), "the gateway refund is queued");
    }

    /** Only refusals decided before anything is written are returned; later failures still roll back. */
    @Test
    void aFailureAfterTheRefundIsWrittenStillRollsTheCallerBack() {
        Order order = acceptedCardOrder(PaymentIntentStatus.SUCCESS);
        doAnswer(invocation -> {
            OutboxEventEntity event = invocation.getArgument(0);
            if (event.getEventType() == EventType.PAYMENT_REFUND_REQUESTED) {
                throw new IllegalStateException("outbox unavailable");
            }
            return invocation.callRealMethod();
        }).when(outbox).save(any(OutboxEventEntity.class));

        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            cancelByRestaurant(order.getId());
            service.requestUnlessRefused(automaticRefund(order));
        }));

        assertEquals(OrderStatus.ACCEPTED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(List.of(), refundsOf(order));
    }

    private Order acceptedCardOrder(PaymentIntentStatus intentStatus) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("53.53"));
        order.setStatus(OrderStatus.ACCEPTED);
        order.setDispatchCityId("BLR");
        order.setFleetSearchRadiusKm(5.0);
        orders.saveAndFlush(order);
        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setInternalOrderId(order.getId());
        intent.setAmount(new BigDecimal("53.53"));
        intent.setStatus(intentStatus);
        intent.setPaymentMethod(PaymentMethod.CARD);
        intent.setGatewayName(PaymentGateway.RAZORPAY);
        intent.setGatewayOrderId("mock_rzp_txn_" + order.getId());
        intents.saveAndFlush(intent);
        return order;
    }

    private void cancelByRestaurant(UUID orderId) {
        Order current = orders.findById(orderId).orElseThrow();
        current.setStatus(OrderStatus.CANCELLED_BY_RESTAURANT);
        orders.save(current);
    }

    /** The command OrderEventConsumer builds for a restaurant cancellation. */
    private static RefundCommand automaticRefund(Order order) {
        return RefundCommand.builder()
                .orderId(order.getId())
                .amount(order.getTotalAmount())
                .faultType(FaultType.RESTAURANT_FAULT)
                .source(RefundSource.RESTAURANT)
                .initiatorType(InitiatorType.SYSTEM)
                .reasonCode("ORDER_CANCELLED_BY_RESTAURANT")
                .idempotencyKey("event_" + order.getId() + "_ORDER_CANCELLED_BY_RESTAURANT")
                .build();
    }

    private List<Refund> refundsOf(Order order) {
        return refunds.findAll().stream().filter(r -> order.getId().equals(r.getOrderId())).toList();
    }

    private long outboxOf(Order order) {
        return outbox.findAll().stream().filter(e -> e.getPayload() != null && e.getPayload().contains(order.getId().toString())).count();
    }
}
