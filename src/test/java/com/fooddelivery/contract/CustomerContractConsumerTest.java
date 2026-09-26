package com.fooddelivery.contract;

import com.fooddelivery.customer.client.AdvertisementClient;
import com.fooddelivery.customer.client.AdvertisementClientFallback;
import com.fooddelivery.customer.client.RestaurantClient;
import com.fooddelivery.customer.client.RestaurantClientFallback;
import com.fooddelivery.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("contract-test")
@SpringBootTest(classes = CustomerContractConsumerTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    // Stub ids are Maven artifactIds; Feign resolves by spring.application.name. These two
    // differ for these services, so the stub must be registered under the name the client asks for.
    "stubrunner.idsToServiceIds.restaurant-application=restaurant-service"
})
@AutoConfigureStubRunner(
    ids = {
        "com.fooddelivery:restaurant-application:+:stubs",
        "com.fooddelivery:bidding-engine:+:stubs",
        "com.fooddelivery:ledger-service:+:stubs"
    }
)
public class CustomerContractConsumerTest {

    @MockBean
    private AdvertisementClientFallback advertisementClientFallback;
    
    @MockBean
    private RestaurantClientFallback restaurantClientFallback;

    @MockBean
    private com.fooddelivery.customer.client.LedgerClientFallback ledgerClientFallback;

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @EnableFeignClients(basePackages = "com.fooddelivery.customer.client")
    static class TestConfig {
    }

    @Autowired
    private AdvertisementClient advertisementClient;
    
    @Autowired
    private RestaurantClient restaurantClient;

    @Autowired
    private com.fooddelivery.customer.client.LedgerClient ledgerClient;

    @Test
    public void testGetNearbyRestaurants() {
        ApiResponse<List<com.fooddelivery.customer.dto.RestaurantDto>> response = restaurantClient.getNearbyRestaurants(12.9716, 77.5946, 5.0);
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
    }

    @Test
    public void testGetBrandOutlets() {
        ApiResponse<List<com.fooddelivery.customer.dto.RestaurantDto>> response = restaurantClient.getBrandOutlets(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), 12.9716, 77.5946, 5.0);
        assertNotNull(response);
        assertTrue(response.isSuccess());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetRestaurantById() {
        Map<String, Object> response = restaurantClient.getRestaurantById(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertNotNull(response);
        // RestaurantOutletController returns ResponseEntity<ApiResponse<Map<...>>>, and the client
        // declares the raw Map, so the envelope is part of the body: name lives under "data".
        assertEquals(Boolean.TRUE, response.get("success"));
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertNotNull(data);
        assertEquals("Test Restaurant", data.get("name"));
    }

    /** The earnings summary sums the outlet's day, week and month in this zone (RestaurantSummaryService). */
    @Test
    public void testGetOutletSummaryCarriesTheOutletZone() {
        Map<String, String> outlet = restaurantClient.getOutletSummary(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertNotNull(outlet);
        assertEquals("Asia/Kolkata", outlet.get("timeZone"));
        assertEquals("Test Restaurant", outlet.get("name"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFetchAds() {
        // The request body must be what BiddingEngine's fetchAds contract declares -- WireMock
        // matches on the body, and AdRequestDTO has exactly these three fields. Sending anything
        // else 404s, the circuit-breaker fallback fires, and because that fallback is a @MockBean
        // it answers null rather than the real emptyList() -- so the miss looked like a null bug.
        com.fooddelivery.customer.dto.AdRequestDTO req = com.fooddelivery.customer.dto.AdRequestDTO.builder()
                .geo("test-geo")
                .deviceId("device-123")
                .context("test-context")
                .build();

        Object response = advertisementClient.fetchAds(req);

        assertNotNull(response);
        // Assert the contracted payload, not merely non-null: an empty list would also be
        // non-null and would mean the stub was never matched.
        List<com.fooddelivery.customer.dto.SponsoredListingDTO> ads = (List<com.fooddelivery.customer.dto.SponsoredListingDTO>) response;
        assertEquals(1, ads.size());
        assertEquals("AD-12345-campaign-1", ads.get(0).getAdId());
        assertEquals("campaign-1", ads.get(0).getCampaignId());
    }

    /**
     * An outlet's clawbacks for a window, as RestaurantSummaryService asks for them. The stub only
     * matches ISO-8601 UTC instants for from/to, so this also proves how Feign writes an Instant.
     */
    @Test
    public void testGetCategoryTotalForAnOutletsClawbacks() {
        java.math.BigDecimal total = ledgerClient.getCategoryTotal(
                com.fooddelivery.common.enums.LedgerAccountType.RESTAURANT_PAYABLE,
                UUID.fromString("0c9a8b7d-1e2f-4a3b-8c4d-5e6f7a8b9c01"),
                com.fooddelivery.common.enums.ChargeCategory.CLAWBACK,
                com.fooddelivery.common.enums.TransactionDirection.DEBIT,
                java.time.Instant.parse("2026-10-18T23:00:00Z"), java.time.Instant.parse("2026-10-26T00:00:00Z"));
        assertNotNull(total);
        assertEquals(0, new java.math.BigDecimal("42.50").compareTo(total));
    }
}
