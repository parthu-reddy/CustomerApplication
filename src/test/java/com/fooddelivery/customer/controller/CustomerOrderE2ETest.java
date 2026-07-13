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
        java.util.Map<String, Object> dataMap = new java.util.HashMap<>();
        dataMap.put("id", restaurantId.toString());
        dataMap.put("name", "Test Restaurant");
        dataMap.put("isActive", true);
        dataMap.put("isOpen", true);
        dataMap.put("lat", 12.9716);
        dataMap.put("lng", 77.5946);

        java.util.Map<String, Object> apiResponse = new java.util.HashMap<>();
        apiResponse.put("success", true);
        apiResponse.put("message", "Restaurant fetched successfully");
        apiResponse.put("data", dataMap);

        ResponseEntity<java.util.Map> response = new ResponseEntity<>(apiResponse, HttpStatus.OK);
        
        java.util.Map<String, Object> mapsResponseMap = new java.util.HashMap<>();
        mapsResponseMap.put("available", true);
        ResponseEntity<java.util.Map> mapsResponseEntity = new ResponseEntity<>(mapsResponseMap, HttpStatus.OK);

        Mockito.when(restTemplate.getForEntity(ArgumentMatchers.contains("restaurants"), ArgumentMatchers.eq(java.util.Map.class)))
            .thenReturn(response);
            
        Mockito.when(restTemplate.getForEntity(ArgumentMatchers.contains("fleet"), ArgumentMatchers.eq(java.util.Map.class)))
            .thenReturn(mapsResponseEntity);
            
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
            .header("X-User-Id", customerId.toString())
            .header("X-User-Role", "CUSTOMER")
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
