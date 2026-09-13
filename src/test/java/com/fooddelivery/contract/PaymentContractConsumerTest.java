package com.fooddelivery.contract;

import com.fooddelivery.common.client.PaymentServiceClient;
import com.fooddelivery.common.dto.payment.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.mockito.Mockito;

@SpringBootTest(classes = PaymentContractConsumerTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureStubRunner(
        ids = {"com.fooddelivery:payment-service:+:stubs"}
)
@ActiveProfiles("contract-test")
public class PaymentContractConsumerTest {

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @EnableFeignClients(basePackages = {"com.fooddelivery.customer.client", "com.fooddelivery.common.client"})
    static class TestConfig {
        @Bean
        public com.fooddelivery.common.client.PaymentServiceClientFallback paymentServiceClientFallback() {
            return Mockito.mock(com.fooddelivery.common.client.PaymentServiceClientFallback.class);
        }
    }

    @MockBean
    private org.springframework.kafka.core.KafkaTemplate kafkaTemplate;
    @MockBean
    private com.fooddelivery.customer.client.RestaurantClient restaurantClient;
    @MockBean
    private com.fooddelivery.customer.client.AdvertisementClient advertisementClient;
    @MockBean
    private com.fooddelivery.common.client.MapsServiceClient mapsServiceClient;

    @Autowired
    private PaymentServiceClient paymentServiceClient;

    @Test
    public void shouldCreateOrder() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setInternalOrderId("123e4567-e89b-12d3-a456-426614174000");
        req.setAmountInInr(new BigDecimal("50.00"));
        req.setPaymentMethod(com.fooddelivery.common.enums.PaymentMethod.CARD);

        String response = paymentServiceClient.createOrder("RAZORPAY", req);
        assertNotNull(response);
    }

}
