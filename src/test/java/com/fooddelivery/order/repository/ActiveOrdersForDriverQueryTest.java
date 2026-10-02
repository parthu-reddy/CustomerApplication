package com.fooddelivery.order.repository;

import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.common.enums.OrderStatus;
import com.fooddelivery.order.entity.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rider's active list against a real database: an order the delivery service has committed to the
 * driver is theirs before DRIVER_ASSIGNED arrives, but never one this service records for another driver.
 */
@DataJpaTest(showSql = false)
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = {"com.fooddelivery.order.entity", "com.fooddelivery.common.outbox.entity"})
@EnableJpaRepositories(basePackages = {"com.fooddelivery.order.repository", "com.fooddelivery.common.outbox.repository"})
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:activeOrdersForDriver;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false"})
class ActiveOrdersForDriverQueryTest {
    static final List<OrderStatus> CANCELLED = List.of(OrderStatus.CANCELLED, OrderStatus.CANCELLED_BY_RESTAURANT);
    static final List<DeliveryStatus> TERMINAL = List.of(DeliveryStatus.DELIVERED, DeliveryStatus.FAILED, DeliveryStatus.CANCELLED);

    @Autowired IOrderRepository orders;

    private Order order(OrderStatus status, UUID driverId) {
        Order order = new Order();
        order.setId(UUID.randomUUID()); order.setCustomerId(UUID.randomUUID()); order.setRestaurantId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("43.35")); order.setStatus(status); order.setDeliveryExecutiveId(driverId);
        order.setDispatchCityId("BLR"); order.setFleetSearchRadiusKm(5.0);
        return orders.saveAndFlush(order);
    }

    private Set<UUID> active(UUID driverId, Collection<UUID> confirmed) {
        Set<UUID> ids = new HashSet<>();
        orders.findActiveOrdersForDriver(driverId, confirmed, CANCELLED, TERMINAL, PageRequest.of(0, 50))
                .forEach(o -> ids.add(o.getId()));
        return ids;
    }

    @Test
    void anOrderConfirmedByTheDeliveryServiceIsTheDriversBeforeItsEventArrives() {
        UUID driver = UUID.randomUUID();
        Order recorded = order(OrderStatus.ACCEPTED, driver);
        Order justAccepted = order(OrderStatus.ACCEPTED, null);
        Order unrelated = order(OrderStatus.ACCEPTED, null);

        assertThat(active(driver, List.of(justAccepted.getId())))
                .containsExactlyInAnyOrder(recorded.getId(), justAccepted.getId())
                .doesNotContain(unrelated.getId());
    }

    @Test
    void anotherDriversOrderIsNeverReturnedEvenIfNamed() {
        UUID driver = UUID.randomUUID();
        Order othersOrder = order(OrderStatus.ACCEPTED, UUID.randomUUID());
        Order cancelled = order(OrderStatus.CANCELLED, null);

        assertThat(active(driver, List.of(othersOrder.getId(), cancelled.getId()))).isEmpty();
    }

    @Test
    void withNoConfirmedOrdersOnlyTheRecordedOnesAreReturned() {
        UUID driver = UUID.randomUUID();
        Order recorded = order(OrderStatus.ACCEPTED, driver);
        order(OrderStatus.ACCEPTED, null);

        assertThat(active(driver, List.of())).containsExactly(recorded.getId());
    }
}
