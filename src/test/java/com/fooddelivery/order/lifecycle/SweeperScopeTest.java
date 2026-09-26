package com.fooddelivery.order.lifecycle;

import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stuck-order sweeper's query, run for real.
 *
 * <p>This is the one guard in Phase 3 that a mock cannot check: the whole point of
 * {@code findStuckInRestaurantStates} is the {@code NOT IN :endedDelivery} clause, and a mocked
 * repository would return whatever the test told it to. Without that clause the sweeper collected
 * orders whose dispatch or delivery had already failed and cancelled them a second time as
 * CANCELLED_BY_RESTAURANT — billing the restaurant for the platform's failure.
 *
 * <p>H2 in Postgres mode, schema from the entities. No broker, no Postgres, no Flyway.
 */
@DataJpaTest(showSql = false)
@EnableJpaRepositories(basePackages = "com.fooddelivery.order.repository")
@EntityScan(basePackages = "com.fooddelivery.order.entity")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sweeperscope;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
})
class SweeperScopeTest {

    @Autowired
    private IOrderRepository orderRepository;

    private static final Instant CUTOFF = Instant.now().plus(java.time.Duration.ofHours(1));

    private static final List<OrderStatus> STUCK_STATUSES =
            List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING);
    private static final List<DeliveryStatus> ENDED_DELIVERY =
            List.of(DeliveryStatus.FAILED, DeliveryStatus.CANCELLED, DeliveryStatus.DELIVERED);

    private UUID save(OrderStatus status, DeliveryStatus deliveryStatus) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCustomerId(UUID.randomUUID());
        o.setRestaurantId(UUID.randomUUID());
        o.setTotalAmount(new BigDecimal("420.00"));
        o.setStatus(status);
        o.setDeliveryStatus(deliveryStatus);
        o.setDispatchCityId("BLR");
        o.setFleetSearchRadiusKm(5.0);
        orderRepository.saveAndFlush(o);
        return o.getId();
    }

    private List<UUID> swept() {
        return orderRepository
                .findStuckInRestaurantStates(STUCK_STATUSES, ENDED_DELIVERY, CUTOFF, PageRequest.of(0, 100))
                .getContent().stream().map(Order::getId).toList();
    }

    @BeforeEach
    void clean() {
        orderRepository.deleteAll();
    }

    @Test
    void aGenuinelyStuckOrderIsSwept() {
        UUID stuck = save(OrderStatus.ACCEPTED, null);

        assertThat(swept()).containsExactly(stuck);
    }

    @Test
    void anOrderWhoseDispatchAlreadyFailedIsNotSweptAgain() {
        save(OrderStatus.ACCEPTED, DeliveryStatus.FAILED);

        assertThat(swept())
                .describedAs("its delivery already failed; cancelling it as the restaurant's fault "
                        + "is the exact defect this clause exists to stop")
                .isEmpty();
    }

    @Test
    void aDeliveredOrderIsNotSwept() {
        save(OrderStatus.ACCEPTED, DeliveryStatus.DELIVERED);

        assertThat(swept()).isEmpty();
    }

    @Test
    void aCancelledDeliveryIsNotSwept() {
        save(OrderStatus.PREPARING, DeliveryStatus.CANCELLED);

        assertThat(swept()).isEmpty();
    }

    @Test
    void anOrderStillOutForDeliveryIsNotSwept() {
        save(OrderStatus.PREPARING, DeliveryStatus.OUT_FOR_DELIVERY);

        assertThat(swept())
                .describedAs("OUT_FOR_DELIVERY is not an ended delivery, but the order is not stuck "
                        + "in a restaurant state either -- it is in the sweep only if its status is")
                .hasSize(1);
    }

    @Test
    void readyForPickupIsOutOfScopeEntirely() {
        save(OrderStatus.READY_FOR_PICKUP, DeliveryStatus.ASSIGNED);

        assertThat(swept())
                .describedAs("a rider is en route; the restaurant has not stalled")
                .isEmpty();
    }

    @Test
    void aTerminalOrderIsNeverSwept() {
        save(OrderStatus.CANCELLED_BY_PLATFORM, DeliveryStatus.FAILED);
        save(OrderStatus.DELIVERY_FAILED, DeliveryStatus.FAILED);

        assertThat(swept()).isEmpty();
    }

    @Test
    void anOrderTouchedAfterTheCutoffIsNotSwept() {
        save(OrderStatus.ACCEPTED, null);

        assertThat(orderRepository.findStuckInRestaurantStates(STUCK_STATUSES, ENDED_DELIVERY,
                        Instant.now().minus(java.time.Duration.ofHours(1)), PageRequest.of(0, 100)).getContent())
                .describedAs("updatedAt is after the cutoff, so it has been touched recently")
                .isEmpty();
    }
}
