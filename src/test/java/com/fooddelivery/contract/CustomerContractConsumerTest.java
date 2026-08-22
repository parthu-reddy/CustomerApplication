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
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
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
        "com.fooddelivery:restaurant-application:+:stubs:8091",
        "com.fooddelivery:bidding-engine:+:stubs:8093"
    },
    stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
public class CustomerContractConsumerTest {

    @MockBean
    private AdvertisementClientFallback advertisementClientFallback;
    
    @MockBean
    private RestaurantClientFallback restaurantClientFallback;

    @Configuration
    @EnableAutoConfiguration(exclude = {
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

    @Test
    public void testGetNearbyRestaurants() {
        ApiResponse<List<Object>> response = restaurantClient.getNearbyRestaurants(12.9716, 77.5946, 5.0);
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
    }

    @Test
    public void testGetBrandOutlets() {
        ApiResponse<List<Object>> response = restaurantClient.getBrandOutlets(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), 12.9716, 77.5946, 5.0);
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

    @SuppressWarnings("unchecked")
    @Test
    public void testFetchAds() {
        // The request body must be what BiddingEngine's fetchAds contract declares -- WireMock
        // matches on the body, and AdRequestDTO has exactly these three fields. Sending anything
        // else 404s, the circuit-breaker fallback fires, and because that fallback is a @MockBean
        // it answers null rather than the real emptyList() -- so the miss looked like a null bug.
        Map<String, Object> req = new HashMap<>();
        req.put("geo", "test-geo");
        req.put("deviceId", "device-123");
        req.put("context", "test-context");

        Object response = advertisementClient.fetchAds(req);

        assertNotNull(response);
        // Assert the contracted payload, not merely non-null: an empty list would also be
        // non-null and would mean the stub was never matched.
        List<Map<String, Object>> ads = (List<Map<String, Object>>) response;
        assertEquals(1, ads.size());
        assertEquals("AD-12345-campaign-1", ads.get(0).get("adId"));
        assertEquals("campaign-1", ads.get(0).get("campaignId"));
    }
}
