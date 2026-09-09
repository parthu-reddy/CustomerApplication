package com.fooddelivery.customer;

import com.fooddelivery.common.client.WalletServiceClient;
import com.fooddelivery.common.dto.wallet.WalletDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(classes = WalletContractConsumerTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureStubRunner(ids = "com.fooddelivery:wallet-service:+:stubs", stubsMode = StubRunnerProperties.StubsMode.LOCAL)
@org.springframework.test.context.ActiveProfiles("contract-test")
public class WalletContractConsumerTest {


    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @EnableFeignClients(clients = WalletServiceClient.class)
    static class TestConfig {
    }

    @Autowired
    private WalletServiceClient walletServiceClient;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    public void shouldReturnWalletBalance() {
        Object rawResponse = walletServiceClient.getWallet("CUSTOMER", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        WalletDto response = objectMapper.convertValue(rawResponse, WalletDto.class);
        
        assertNotNull(response);
        assertEquals("123e4567-e89b-12d3-a456-426614174000", response.getEntityId().toString());
        assertEquals(500.00, response.getBalance().doubleValue());
    }

}
