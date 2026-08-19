package com.fooddelivery.contract;

import com.fooddelivery.customer.client.AdvertisementClient;
import com.fooddelivery.customer.client.AdvertisementClientFallback;
import com.fooddelivery.customer.client.RestaurantClient;
import com.fooddelivery.customer.client.RestaurantClientFallback;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("contract-test")
@SpringBootTest(classes = CustomerContractConsumerTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureStubRunner(ids = { "com.fooddelivery:restaurant-application:+:stubs:8091", "com.fooddelivery:delivery-executive-application:+:stubs:8092" }, stubsMode = StubRunnerProperties.StubsMode.LOCAL)
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
    public void contextLoads() {
        assertNotNull(advertisementClient);
        assertNotNull(restaurantClient);
    }
}
