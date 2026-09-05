package com.fooddelivery.contract;

import com.fooddelivery.common.client.MapsServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.mockito.Mockito;

@SpringBootTest(classes = MapsContractConsumerTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureStubRunner(
        stubsMode = StubRunnerProperties.StubsMode.LOCAL,
        ids = {"com.fooddelivery:mapsintegration:+:stubs"}
)
@ActiveProfiles("contract-test")
public class MapsContractConsumerTest {

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @EnableFeignClients(basePackages = {"com.fooddelivery.customer.client", "com.fooddelivery.common.client"})
    static class TestConfig {
        @Bean
        public com.fooddelivery.common.client.MapsServiceClientFallback mapsServiceClientFallback() {
            return Mockito.mock(com.fooddelivery.common.client.MapsServiceClientFallback.class);
        }
    }

    @MockBean
    private org.springframework.kafka.core.KafkaTemplate kafkaTemplate;
    @MockBean
    private com.fooddelivery.customer.client.RestaurantClient restaurantClient;
    @MockBean
    private com.fooddelivery.customer.client.AdvertisementClient advertisementClient;
    @MockBean
    private com.fooddelivery.common.client.PaymentServiceClient paymentServiceClient;

    @Autowired
    private MapsServiceClient mapsServiceClient;

    @Test
    public void shouldReverseGeocode() {
        com.fooddelivery.common.dto.maps.PlaceGeocodeDto response = mapsServiceClient.reverseGeocode(12.971598, 77.594562);
        assertNotNull(response);
    }
}
