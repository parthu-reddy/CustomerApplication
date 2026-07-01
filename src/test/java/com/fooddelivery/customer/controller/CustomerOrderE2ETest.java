package com.fooddelivery.customer.controller;

import com.fooddelivery.common.test.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.UUID;
import com.fooddelivery.customer.repository.ICustomerRepository;
import com.fooddelivery.customer.repository.CustomerAddressRepository;
import com.fooddelivery.customer.entity.Customer;
import com.fooddelivery.customer.entity.CustomerAddress;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@SpringBootTest(classes = com.fooddelivery.FoodDeliveryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerOrderE2ETest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private ICustomerRepository customerRepository;

    @Autowired
    private CustomerAddressRepository customerAddressRepository;

    private UUID restaurantId = UUID.randomUUID();
    private UUID menuItemId = UUID.randomUUID();
    private UUID customerId = UUID.randomUUID();
    private UUID deliveryAddressId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        
        // Mock Restaurant API
        com.fooddelivery.customer.service.CustomerOrderService.RestaurantDTO activeRestaurant = 
            new com.fooddelivery.customer.service.CustomerOrderService.RestaurantDTO(restaurantId, "Test Restaurant", true, 12.9716, 77.5946);
        ResponseEntity<com.fooddelivery.customer.service.CustomerOrderService.RestaurantDTO> response = 
            new ResponseEntity<>(activeRestaurant, HttpStatus.OK);
            
        Mockito.when(restTemplate.getForEntity(ArgumentMatchers.anyString(), ArgumentMatchers.eq(com.fooddelivery.customer.service.CustomerOrderService.RestaurantDTO.class)))
            .thenReturn(response);
            
        // Mock Menu API
        com.fooddelivery.customer.service.CustomerOrderService.MenuItemDTO menuItem = 
            new com.fooddelivery.customer.service.CustomerOrderService.MenuItemDTO(
                menuItemId, restaurantId, "Burger", new BigDecimal("10.00"), true, 15
            );
        ResponseEntity<List<com.fooddelivery.customer.service.CustomerOrderService.MenuItemDTO>> menuResponse = 
            new ResponseEntity<>(List.of(menuItem), HttpStatus.OK);
            
        Mockito.when(restTemplate.exchange(
            ArgumentMatchers.anyString(), 
            ArgumentMatchers.eq(HttpMethod.GET), 
            ArgumentMatchers.isNull(), 
            ArgumentMatchers.<ParameterizedTypeReference<List<com.fooddelivery.customer.service.CustomerOrderService.MenuItemDTO>>>any()
        )).thenReturn(menuResponse);
            
        // Mock Payment Gateway API
        Mockito.when(restTemplate.postForEntity(ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.eq(String.class)))
            .thenReturn(new ResponseEntity<>("mocked_gateway_order_id_123", HttpStatus.OK));

        Customer customer = Customer.builder()
                .name("Test User")
                .email("test" + UUID.randomUUID() + "@example.com")
                .phoneNumber("+123" + (int)(Math.random() * 10000000))
                .build();
        customer = customerRepository.save(customer);
        customerId = customer.getId();

        CustomerAddress address = CustomerAddress.builder()
                .id(deliveryAddressId)
                .customerId(customerId)
                .addressLine1("123 Main St")
                .city("Bangalore")
                .state("KA")
                .zipCode("560001")
                .latitude(12.9715)
                .longitude(77.5945)
                .build();
        customerAddressRepository.save(address);
    }

    @Test
    void createOrder_ShouldReturnPaymentIntentAndOrderDetails() {
        Map<String, Object> request = Map.of(
                "customerId", customerId.toString(),
                "restaurantId", restaurantId.toString(),
                "deliveryAddressId", deliveryAddressId.toString(),
                "items", List.of(
                        Map.of(
                                "menuItemId", menuItemId.toString(),
                                "quantity", 2,
                                "price", 15.50
                        )
                )
        );

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/orders")
        .then()
            .statusCode(200)
            .body("data.id", notNullValue())
            .body("data.status", equalTo("CREATED"))
            .body("data.paymentIntent", equalTo("mocked_gateway_order_id_123"));
    }
}
