package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.entity.Customer;
import com.fooddelivery.customer.repository.ICustomerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerProfileController {

    private final ICustomerRepository customerRepository;
    private final com.fooddelivery.customer.repository.CustomerAddressRepository addressRepository;

    public CustomerProfileController(ICustomerRepository customerRepository, com.fooddelivery.customer.repository.CustomerAddressRepository addressRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getProfile(
            java.security.Principal principal,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        
        String userIdStr = (principal != null && principal.getName() != null) ? principal.getName() : headerUserId;
        if (userIdStr == null) {
            throw new org.springframework.security.access.AccessDeniedException("User identity not found in request");
        }
        
        UUID customerId = UUID.fromString(userIdStr);
        Customer customer = customerRepository.findById(customerId).orElseGet(() -> {
            Customer newCustomer = new Customer();
            newCustomer.setId(customerId);
            return customerRepository.save(newCustomer);
        });
        
        java.util.List<com.fooddelivery.customer.entity.CustomerAddress> addresses = addressRepository.findByCustomerId(customerId);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", customer.getId());
        data.put("phoneNumber", customer.getPhoneNumber());
        data.put("addresses", addresses);
        
        return ResponseEntity.ok(ApiResponse.success(data, "Profile fetched"));
    }

    @PostMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Customer>> createProfile(
            java.security.Principal principal, 
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody Customer newCustomer) {
        
        String userIdStr = (principal != null && principal.getName() != null) ? principal.getName() : headerUserId;
        if (userIdStr == null) {
            throw new org.springframework.security.access.AccessDeniedException("User identity not found in request");
        }
        
        UUID customerId = UUID.fromString(userIdStr);
        Customer customer = customerRepository.findById(customerId).orElse(new Customer());
        customer.setId(customerId);
        if (newCustomer.getPhoneNumber() != null) customer.setPhoneNumber(newCustomer.getPhoneNumber());
        Customer saved = customerRepository.save(customer);
        return ResponseEntity.ok(ApiResponse.success(saved, "Profile created/updated successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER') and #id.toString() == authentication.name")
    public ResponseEntity<ApiResponse<Customer>> updateProfile(
            @PathVariable UUID id,
            @RequestBody Customer updateData) {
        
        Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        if (updateData.getPhoneNumber() != null) {
            customer.setPhoneNumber(updateData.getPhoneNumber());
        }
        
        Customer saved = customerRepository.save(customer);
        return ResponseEntity.ok(ApiResponse.success(saved, "Profile updated successfully"));
    }
}
