package com.fooddelivery.customer;

import com.fooddelivery.common.test.BaseIntegrationTest;
import com.fooddelivery.customer.entity.Customer;
import com.fooddelivery.customer.repository.ICustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CustomerValidationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ICustomerRepository customerRepository;

    @Test
    void shouldThrowExceptionWhenDuplicateEmailIsSaved() {
        // Create first customer
        Customer customer1 = Customer.builder()
                .id(UUID.randomUUID())
                .name("John Doe")
                .phoneNumber("9999999999")
                .email("duplicate@example.com")
                .build();
        customerRepository.save(customer1);

        // Try to create second customer with same email
        Customer customer2 = Customer.builder()
                .id(UUID.randomUUID())
                .name("Jane Doe")
                .phoneNumber("8888888888")
                .email("duplicate@example.com") // Same email
                .build();

        // Expect DataIntegrityViolationException due to unique constraint
        assertThatThrownBy(() -> customerRepository.save(customer2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
