package com.fooddelivery.order.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.customer.config.DynamicPricingConfig;
import com.fooddelivery.customer.model.PricingBreakdown;
import com.fooddelivery.customer.model.PricingRates;
import com.fooddelivery.customer.service.DynamicPricingService;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderCharge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

/**
 * What the ledger says a payee is owed must equal what the order says it will pay them.
 *
 * <p>This class was two methods of `assertTrue(true, "<a sentence>")`, and it lived in LedgerService
 * where it could not see either the pricing service or the bookkeeper. The plan named this as the
 * property `LedgerAccountResolverTest` "should have asserted and does not": if the payable delta and
 * the stored payout disagree, the payout screen and the ledger disagree about what to pay, and one
 * of them is wrong by that difference.
 */
class PayableMatchesPayoutTest {

    private DynamicPricingService pricingService;
    private PricingRates rates;
    private OutboxEventRepository outboxEventRepository;
    private LedgerBookkeeper bookkeeper;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        DynamicPricingConfig config = new DynamicPricingConfig();
        config.setBasePrice(new BigDecimal("15.00"));
        config.setPerKmRate(new BigDecimal("8.00"));
        config.setRestMaxContributionPercent(new BigDecimal("0.15"));
        config.setFixedPlatformFee(new BigDecimal("5.00"));
        config.setPlatformExcessCutPercent(new BigDecimal("0.50"));
        config.setSgstPercent(new BigDecimal("0.025"));
        config.setCgstPercent(new BigDecimal("0.025"));
        config.setDeliverySgstPercent(new BigDecimal("0.09"));
        config.setDeliveryCgstPercent(new BigDecimal("0.09"));
        pricingService = new DynamicPricingService(config);
        rates = config.currentRates();

        outboxEventRepository = Mockito.mock(OutboxEventRepository.class);
        bookkeeper = new LedgerBookkeeper(outboxEventRepository, mapper,
                new LedgerAccountResolver(), Mockito.mock(LedgerClient.class));
    }

    private Order order;

    private LedgerTransactionCommand bookDelivered(String foodCost, String distanceKm) {
        PricingBreakdown pricing = pricingService.calculatePricing(
                new BigDecimal(foodCost), new BigDecimal(distanceKm), rates);

        order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID());
        order.setDeliveryExecutiveId(UUID.randomUUID());
        order.setTotalAmount(pricing.getCustomerTotal());
        order.setRestaurantPayout(pricing.getRestaurantPayout());
        order.setDriverNetPayout(pricing.getDriverNetPayout());
        order.setCharges(pricing.getCharges());
        for (OrderCharge c : pricing.getCharges()) {
            c.setOrder(order);
        }

        bookkeeper.bookDelivered(order);
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        try {
            return mapper.readValue(captor.getValue().getPayload(), LedgerTransactionCommand.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Net movement on one account across the booked legs: credits in, debits out. */
    private BigDecimal delta(LedgerTransactionCommand cmd, LedgerAccountType type, UUID ownerId) {
        BigDecimal net = BigDecimal.ZERO;
        for (LedgerLeg leg : cmd.getLegs()) {
            if (leg.getToType() == type && leg.getToId().equals(ownerId)) {
                net = net.add(leg.getAmount());
            }
            if (leg.getFromType() == type && leg.getFromId().equals(ownerId)) {
                net = net.subtract(leg.getAmount());
            }
        }
        return net;
    }

    @ParameterizedTest(name = "food={0} distance={1}km")
    @CsvSource({
            "200.00, 5.0",
            "200.00, 6.0",
            "1000.00, 1.0",
            "50.00, 12.0",
            "19.99, 0.4",
            "777.77, 3.3"
    })
    void restaurantPayableDeltaEqualsTheStoredRestaurantPayout(String foodCost, String distanceKm) {
        LedgerTransactionCommand cmd = bookDelivered(foodCost, distanceKm);

        assertEquals(0, order.getRestaurantPayout().compareTo(
                        delta(cmd, LedgerAccountType.RESTAURANT_PAYABLE, order.getRestaurantId())),
                "food=" + foodCost + " distance=" + distanceKm
                        + ": order says " + order.getRestaurantPayout()
                        + " but the ledger moves " + delta(cmd, LedgerAccountType.RESTAURANT_PAYABLE, order.getRestaurantId()));
    }

    @ParameterizedTest(name = "food={0} distance={1}km")
    @CsvSource({
            "200.00, 5.0",
            "200.00, 6.0",
            "1000.00, 1.0",
            "50.00, 12.0",
            "19.99, 0.4",
            "777.77, 3.3"
    })
    void driverPayableDeltaEqualsTheStoredNetPayout(String foodCost, String distanceKm) {
        LedgerTransactionCommand cmd = bookDelivered(foodCost, distanceKm);

        assertEquals(0, order.getDriverNetPayout().compareTo(
                        delta(cmd, LedgerAccountType.DRIVER_PAYABLE, order.getDeliveryExecutiveId())),
                "food=" + foodCost + " distance=" + distanceKm
                        + ": order says " + order.getDriverNetPayout()
                        + " but the ledger moves " + delta(cmd, LedgerAccountType.DRIVER_PAYABLE, order.getDeliveryExecutiveId()));
    }
}
