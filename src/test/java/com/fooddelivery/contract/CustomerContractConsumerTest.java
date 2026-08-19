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
@SpringBootTest(classes = CustomerContractConsumerTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
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

    @Test
    public void testGetRestaurantById() {
        Map<String, Object> response = restaurantClient.getRestaurantById(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertNotNull(response);
        assertEquals("Test Restaurant", response.get("name"));
    }

    @Test
    public void testFetchAds() {
        Map<String, Object> req = new HashMap<>();
        req.put("userId", "user123");
        req.put("location", "test");
        Object response = advertisementClient.fetchAds(req);
        assertNotNull(response);
    }
}
