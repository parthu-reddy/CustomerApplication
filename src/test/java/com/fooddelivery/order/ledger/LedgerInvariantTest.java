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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;

/**
 * Double entry balances, for every order the pricing service can produce.
 *
 * <p>This class was three methods of `assertTrue(true, "<a sentence describing the invariant>")`.
 * Gate 4.3 passed because it only checks the class exists. The plan called these invariants "worth
 * more than any number of example tests, because they hold for every order"; nothing held.
 *
 * <p>Driven over the same parameter matrix {@code DynamicPricingServiceTest} uses: distance either
 * side of the 5 km contribution cut-off, and contributions above and below the driver payout.
 */
class LedgerInvariantTest {

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

    private Order pricedOrder(String foodCost, String distanceKm) {
        PricingBreakdown pricing = pricingService.calculatePricing(
                new BigDecimal(foodCost), new BigDecimal(distanceKm), rates);

        Order order = new Order();
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
        return order;
    }

    private LedgerTransactionCommand booked(Order order) {
        bookkeeper.bookDelivered(order);
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        try {
            return mapper.readValue(captor.getValue().getPayload(), LedgerTransactionCommand.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Invariant 1: for any priced order, the legs booked on delivery move the same total out as in.
     * Each leg debits one account and credits another by the same amount, so the sums must agree.
     */
    @ParameterizedTest(name = "food={0} distance={1}km")
    @CsvSource({
            "200.00, 5.0",    // at the contribution cut-off
            "200.00, 6.0",    // beyond it: the restaurant contributes nothing
            "1000.00, 1.0",   // contribution exceeds the driver payout, so a platform bonus appears
            "50.00, 12.0",    // small order, long distance: the customer carries the delivery
            "0.00, 3.0",      // zero-item order
            "19.99, 0.4"      // sub-1km, rounds to the 1km floor
    })
    void debitsEqualCreditsForEveryPricedOrder(String foodCost, String distanceKm) {
        LedgerTransactionCommand cmd = booked(pricedOrder(foodCost, distanceKm));

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerLeg leg : cmd.getLegs()) {
            debits = debits.add(leg.getAmount());   // the from side
            credits = credits.add(leg.getAmount()); // the to side
        }
        assertEquals(0, debits.compareTo(credits),
                "food=" + foodCost + " distance=" + distanceKm + ": " + debits + " out vs " + credits + " in");
        assertFalse(cmd.getLegs().isEmpty(), "a delivered order must book something");
    }

    /**
     * Invariant 1b: no leg may be zero or negative. The ledger rejects those outright, so a pricing
     * combination that produces one turns into a rejected movement in production.
     */
    @ParameterizedTest(name = "food={0} distance={1}km")
    @CsvSource({"200.00, 5.0", "200.00, 6.0", "1000.00, 1.0", "50.00, 12.0", "0.00, 3.0", "19.99, 0.4"})
    void everyLegIsAPositiveAmount(String foodCost, String distanceKm) {
        for (LedgerLeg leg : booked(pricedOrder(foodCost, distanceKm)).getLegs()) {
            assertEquals(1, leg.getAmount().compareTo(BigDecimal.ZERO),
                    leg.getCategory() + " leg is " + leg.getAmount() + "; the ledger rejects non-positive amounts");
        }
    }

    /**
     * Invariant 1c: every account the legs touch nets to zero across the transaction, which is what
     * the reconciliation DOUBLE_ENTRY check verifies per transaction in production.
     */
    @ParameterizedTest(name = "food={0} distance={1}km")
    @CsvSource({"200.00, 5.0", "200.00, 6.0", "1000.00, 1.0", "50.00, 12.0"})
    void theTransactionNetsToZeroAcrossAllAccounts(String foodCost, String distanceKm) {
        Map<String, BigDecimal> net = new HashMap<>();
        for (LedgerLeg leg : booked(pricedOrder(foodCost, distanceKm)).getLegs()) {
            net.merge(key(leg.getFromType(), leg.getFromId()), leg.getAmount().negate(), BigDecimal::add);
            net.merge(key(leg.getToType(), leg.getToId()), leg.getAmount(), BigDecimal::add);
        }
        BigDecimal total = net.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(BigDecimal.ZERO), "accounts do not net to zero: " + net);
    }

    private static String key(LedgerAccountType type, UUID id) {
        return type + ":" + id;
    }
}
