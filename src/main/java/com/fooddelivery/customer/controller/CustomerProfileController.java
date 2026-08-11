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

    public CustomerProfileController(ICustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
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
