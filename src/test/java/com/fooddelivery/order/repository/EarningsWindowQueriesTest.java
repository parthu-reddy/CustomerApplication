package com.fooddelivery.order.repository;

import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two earnings-summary queries, run for real: an outlet's and a rider's orders delivered in
 * {@code [from, to)}. The bounds and the delivered-only rule are in the SQL, so a mocked repository
 * cannot check them.
 *
 * <p>Payout amounts are quoted onto every order when it is placed, but the ledger books them only
 * when the order is delivered ({@code OrderActionService.completeDelivery} sets {@code deliveredAt}
 * and calls {@code bookDelivered}). These queries are what make the summaries agree with the ledger.
 *
 * <p>H2 in Postgres mode, schema from the entities. No broker, no Postgres, no Flyway.
 */
@DataJpaTest(showSql = false)
@EnableJpaRepositories(basePackages = "com.fooddelivery.order.repository")
@EntityScan(basePackages = "com.fooddelivery.order.entity")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:earningswindow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
})
class EarningsWindowQueriesTest {

    /** New York's fall-back day, 25 hours. */
    private static final Instant FROM = Instant.parse("2026-11-01T04:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-02T05:00:00Z");

    private static final UUID OUTLET = UUID.randomUUID();
    private static final UUID OTHER_OUTLET = UUID.randomUUID();
    private static final UUID RIDER = UUID.randomUUID();
    private static final UUID OTHER_RIDER = UUID.randomUUID();

    @Autowired
    private IOrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    /** An order for {@code outletId} carried by {@code riderId}, placed at {@code placedAt}, delivered at {@code deliveredAt} (null: never). */
    private UUID order(UUID outletId, UUID riderId, Instant placedAt, Instant deliveredAt, OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setRestaurantId(outletId);
        order.setDeliveryExecutiveId(riderId);
        order.setTotalAmount(new BigDecimal("420.00"));
        order.setStatus(status);
        order.setDeliveryStatus(deliveredAt != null ? DeliveryStatus.DELIVERED : null);
        order.setDeliveredAt(deliveredAt);
        order.setDispatchCityId("BLR");
        order.setFleetSearchRadiusKm(5.0);
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setName("Dosa");
        item.setQuantity(2);
        item.setPrice(new BigDecimal("120.00"));
        order.getOrderItems().add(item);
        orderRepository.saveAndFlush(order);
        // createdAt is @CreationTimestamp, stamped with "now" on insert. Set the moment afterwards.
        entityManager.getEntityManager()
                .createQuery("UPDATE Order o SET o.createdAt = :at WHERE o.id = :id")
                .setParameter("at", placedAt)
                .setParameter("id", order.getId())
                .executeUpdate();
        return order.getId();
    }

    private UUID delivered(UUID outletId, UUID riderId, Instant deliveredAt) {
        return order(outletId, riderId, deliveredAt.minus(Duration.ofMinutes(40)), deliveredAt, OrderStatus.ACCEPTED);
    }

    private List<Order> outletEarnings() {
        entityManager.clear();
        return orderRepository.findByRestaurantIdDeliveredInWindow(OUTLET, FROM, TO);
    }

    private List<UUID> riderEarnings() {
        entityManager.clear();
        return orderRepository.findByDeliveryExecutiveIdDeliveredInWindow(RIDER, FROM, TO).stream().map(Order::getId).toList();
    }

    // ---- the window, on deliveredAt ----

    @Test
    void theWindowIncludesItsFirstInstantAndExcludesItsLast() {
        delivered(OUTLET, RIDER, FROM.minusMillis(1));
        UUID atFrom = delivered(OUTLET, RIDER, FROM);
        UUID lastInside = delivered(OUTLET, RIDER, TO.minusMillis(1));
        delivered(OUTLET, RIDER, TO);

        assertThat(outletEarnings()).extracting(Order::getId).containsExactlyInAnyOrder(atFrom, lastInside);
        assertThat(riderEarnings()).containsExactlyInAnyOrder(atFrom, lastInside);
    }

    /** Placed at 23:50 the evening before, delivered after midnight: earned on the day it was delivered. */
    @Test
    void anOrderCountsOnTheDayItWasDeliveredNotTheDayItWasPlaced() {
        UUID placedBeforeDeliveredInside = order(OUTLET, RIDER, FROM.minus(Duration.ofMinutes(10)), FROM.plus(Duration.ofMinutes(30)), OrderStatus.ACCEPTED);
        order(OUTLET, RIDER, TO.minus(Duration.ofMinutes(10)), TO.plus(Duration.ofMinutes(30)), OrderStatus.ACCEPTED);

        assertThat(outletEarnings()).extracting(Order::getId).containsExactly(placedBeforeDeliveredInside);
        assertThat(riderEarnings()).containsExactly(placedBeforeDeliveredInside);
    }

    // ---- only what was delivered ----

    /** A cancelled order still carries the payout quoted when it was placed; the ledger never pays it. */
    @Test
    void anOrderThatWasNeverDeliveredEarnsNothing() {
        UUID delivered = delivered(OUTLET, RIDER, FROM.plus(Duration.ofHours(2)));
        order(OUTLET, RIDER, FROM.plus(Duration.ofHours(1)), null, OrderStatus.CANCELLED);
        order(OUTLET, RIDER, FROM.plus(Duration.ofHours(1)), null, OrderStatus.CANCELLED_BY_RESTAURANT);
        order(OUTLET, RIDER, FROM.plus(Duration.ofHours(3)), null, OrderStatus.PREPARING);

        assertThat(outletEarnings()).extracting(Order::getId).containsExactly(delivered);
        assertThat(riderEarnings()).containsExactly(delivered);
    }

    @Test
    void anotherOutletsAndAnotherRidersOrdersAreNotCounted() {
        UUID mine = delivered(OUTLET, RIDER, FROM.plusSeconds(60));
        delivered(OTHER_OUTLET, OTHER_RIDER, FROM.plusSeconds(60));

        assertThat(outletEarnings()).extracting(Order::getId).containsExactly(mine);
        assertThat(riderEarnings()).containsExactly(mine);
    }

    /**
     * The outlet summary sums items outside any transaction, and open-in-view is off in the deployed
     * config, so a lazy collection here would throw there.
     */
    @Test
    void theOutletsItemsArriveWithTheOrders() {
        delivered(OUTLET, RIDER, FROM.plusSeconds(60));

        List<Order> orders = outletEarnings();

        assertThat(orders).hasSize(1);
        assertThat(Hibernate.isInitialized(orders.get(0).getOrderItems())).isTrue();
        assertThat(orders.get(0).getOrderItems()).hasSize(1);
    }
}
