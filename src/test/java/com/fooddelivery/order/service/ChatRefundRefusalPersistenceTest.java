package com.fooddelivery.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.PaymentIntentStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.common.enums.PaymentGateway;
import com.fooddelivery.common.enums.PaymentMethod;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.RefundStatus;
import com.fooddelivery.common.event.EventBinder;
import com.fooddelivery.common.event.OutboxEvent;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.entity.PaymentIntent;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.entity.RefundItem;
import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.order.enums.InitiatorType;
import com.fooddelivery.order.enums.RefundSource;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.order.repository.RefundRepository;
import com.fooddelivery.order.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A refusal raised inside {@link RefundService} must still reach the customer as CHAT_REFUND_ERROR.
 *
 * <p>Live on 2026-10-02, a second quote for an already-refunded item made {@code RefundService.quote}
 * throw ITEM_ALREADY_REFUNDED through its {@code @Transactional} proxy. That marked the listener's
 * write transaction rollback-only, so the commit threw UnexpectedRollbackException, the error reply
 * and the idempotency key rolled back, and the request was dead-lettered: the customer got no answer.
 * {@link ChatRefundProcessorServiceTest} mocks the TransactionTemplate and RefundService, so it cannot
 * see this. Here the proxies, transactions and rows are real (H2, PostgreSQL mode); nothing is mocked
 * on the path under test.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = {"com.fooddelivery.order.entity", "com.fooddelivery.common.outbox.entity",
        "com.fooddelivery.common.entity"})
@EnableJpaRepositories(basePackages = {"com.fooddelivery.order.repository",
        "com.fooddelivery.common.outbox.repository", "com.fooddelivery.common.repository"})
@Import({RefundService.class, ChatRefundProcessorService.class, ChatRefundRefusalPersistenceTest.Config.class})
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:chatRefundRefusal;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatRefundRefusalPersistenceTest {

    @TestConfiguration
    static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean EventBinder eventBinder(ObjectMapper mapper) {
            return new EventBinder(mapper, jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
        }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    @Autowired ChatRefundProcessorService processor;
    @Autowired IOrderRepository orders;
    @Autowired IPaymentIntentRepository intents;
    @Autowired RefundRepository refunds;
    @Autowired OutboxEventRepository outbox;
    @Autowired IIdempotencyKeyRepository idempotencyKeys;
    @Autowired SupportTicketRepository tickets;
    @MockBean LedgerBookkeeper ledger;

    private final ObjectMapper json = new ObjectMapper();
    private UUID customerId;
    private UUID orderId;
    private UUID refundedItem;
    private UUID openItem;

    /** A delivered order of two items; the first was refunded by a COMPLETED item refund. */
    @BeforeEach
    void deliveredOrderWithOneRefundedItem() {
        customerId = UUID.randomUUID();
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(customerId);
        order.setRestaurantId(UUID.randomUUID());
        order.setStatus(OrderStatus.HANDED_OVER);
        order.setItemTotal(new BigDecimal("40.00"));
        order.setSgst(new BigDecimal("1.00"));
        order.setCgst(new BigDecimal("1.00"));
        order.setTotalAmount(new BigDecimal("60.00"));
        order.setDispatchCityId("BLR");
        order.setFleetSearchRadiusKm(5.0);
        order.getOrderItems().add(item(order, "Refunded dish", "28.00"));
        order.getOrderItems().add(item(order, "Untouched dish", "12.00"));
        orders.saveAndFlush(order);
        orderId = order.getId();
        refundedItem = idOf(order, "Refunded dish");
        openItem = idOf(order, "Untouched dish");

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setInternalOrderId(orderId);
        intent.setAmount(new BigDecimal("60.00"));
        intent.setStatus(PaymentIntentStatus.SUCCESS);
        intent.setPaymentMethod(PaymentMethod.CARD);
        intent.setGatewayName(PaymentGateway.RAZORPAY);
        intent.setGatewayOrderId("mock_rzp_txn_" + orderId);
        intents.saveAndFlush(intent);

        Refund award = Refund.builder().id(UUID.randomUUID()).orderId(orderId).paymentIntentId(intent.getId())
                .amount(new BigDecimal("12.00")).currency("INR").reasonCode("CUSTOMER_TICKET")
                .reasonText("Earlier support award").faultType(FaultType.RESTAURANT_FAULT)
                .destination(RefundDestination.ORIGINAL_METHOD).source(RefundSource.CUSTOMER_TICKET)
                .initiatedByType(InitiatorType.ADMIN).status(RefundStatus.COMPLETED)
                .idempotencyKey(UUID.randomUUID().toString()).build();
        RefundItem consumed = new RefundItem();
        consumed.setId(UUID.randomUUID());
        consumed.setRefund(award);
        consumed.setOrderItemId(refundedItem);
        consumed.setQuantity(1);
        award.setRefundItems(Set.of(consumed));
        refunds.saveAndFlush(award);
    }

    @Test
    void quoteForAnAlreadyRefundedItemCommitsAnErrorReply() throws Exception {
        OutboxEvent quote = chatEvent("CHAT_REFUND_QUOTE_REQUESTED", refundedItem);

        assertDoesNotThrow(() -> processor.handleChatEvents(quote), "a business refusal is an answer, not a retry");

        assertEquals(List.of("ITEM_ALREADY_REFUNDED"), errorsFor(quote));
        assertTrue(idempotencyKeys.existsById("chat_event:" + quote.getId()), "the refusal is recorded once");
    }

    @Test
    void refundRequestForAnAlreadyRefundedItemCommitsAnErrorReplyAndNoTicket() throws Exception {
        OutboxEvent request = chatEvent("CHAT_REFUND_REQUESTED", refundedItem);

        assertDoesNotThrow(() -> processor.handleChatEvents(request));

        assertEquals(List.of("ITEM_ALREADY_REFUNDED"), errorsFor(request));
        assertEquals(0, tickets.count());
        assertTrue(idempotencyKeys.existsById("chat_event:" + request.getId()));
    }

    /** Control: the same harness commits a real quote, so the refusal tests fail for the right reason. */
    @Test
    void quoteForAnUntouchedItemCommitsTheQuote() throws Exception {
        OutboxEvent quote = chatEvent("CHAT_REFUND_QUOTE_REQUESTED", openItem);

        processor.handleChatEvents(quote);

        List<OutboxEventEntity> replies = repliesFor(quote);
        assertEquals(1, replies.size());
        assertEquals(EventType.CHAT_REFUND_QUOTE_RESPONSE, replies.get(0).getEventType());
        // 12.00 of 40.00 items: 12.00 + 30% of each 1.00 tax.
        assertEquals(0, new BigDecimal("12.60").compareTo(
                new BigDecimal(json.readTree(replies.get(0).getPayload()).path("quoteAmount").asText())));
    }

    private OutboxEvent chatEvent(String type, UUID itemId) throws Exception {
        String payload = json.writeValueAsString(java.util.Map.of(
                "orderId", orderId.toString(), "refundType", "PARTIAL", "reason", "E2E checkpoint24",
                "actorId", customerId.toString(), "actorType", "CUSTOMER",
                "items", List.of(java.util.Map.of("itemId", itemId.toString(), "quantity", 1))));
        return OutboxEvent.builder().id(UUID.randomUUID().toString()).type(type)
                .aggregateId(UUID.randomUUID().toString()).payload(payload).build();
    }

    private List<OutboxEventEntity> repliesFor(OutboxEvent event) {
        return outbox.findAll().stream().filter(e -> event.getAggregateId().equals(e.getAggregateId())).toList();
    }

    private List<String> errorsFor(OutboxEvent event) throws Exception {
        List<String> errors = new java.util.ArrayList<>();
        for (OutboxEventEntity reply : repliesFor(event)) {
            assertEquals(EventType.CHAT_REFUND_ERROR, reply.getEventType());
            errors.add(json.readTree(reply.getPayload()).path("error").asText());
        }
        return errors;
    }

    private static OrderItem item(Order order, String name, String price) {
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setMenuItemId(UUID.randomUUID());
        item.setName(name);
        item.setQuantity(1);
        item.setPrice(new BigDecimal(price));
        return item;
    }

    private static UUID idOf(Order order, String name) {
        return order.getOrderItems().stream().filter(i -> name.equals(i.getName())).findFirst().orElseThrow().getId();
    }
}
