package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.entity.Customer;
import com.fooddelivery.customer.repository.ICustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import com.fooddelivery.customer.dto.CustomerProfileRequest;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('CUSTOMER')")
public class CustomerProfileController {

    private final ICustomerRepository customerRepository;

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<String>> updateProfile(
            @RequestHeader("X-User-Id") String userId, 
            @RequestHeader(value = "X-User-Phone", required = false) String phone,
            @Valid @RequestBody CustomerProfileRequest request) {
        
        Customer customer = customerRepository.findById(UUID.fromString(userId))
                .orElseGet(() -> Customer.builder()
                        .id(UUID.fromString(userId))
                        .phoneNumber(phone != null ? phone : "0000" + UUID.randomUUID().toString().substring(0,6))
                        .name("Customer")
                        .build());
        
        if (request.getName() != null) {
            customer.setName(request.getName());
        }
        if (request.getEmail() != null) {
            customer.setEmail(request.getEmail());
        }
        
        try {
            customerRepository.save(customer);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email or Phone already in use"));
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Customer profile updated"));
    }
}
