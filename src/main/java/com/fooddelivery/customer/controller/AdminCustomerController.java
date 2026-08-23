package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.dto.CustomerAddressDto;
import com.fooddelivery.customer.entity.CustomerAddress;
import com.fooddelivery.customer.repository.CustomerAddressRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import java.util.stream.Collectors;
import com.fooddelivery.customer.repository.ICustomerRepository;
import com.fooddelivery.customer.entity.Customer;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/internal/admin/customers")
@lombok.extern.slf4j.Slf4j
public class AdminCustomerController {
    

    private final CustomerAddressRepository addressRepository;
    private final ICustomerRepository customerRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<CustomerAddressDto>>> getAllCustomerAddresses(
            @org.springframework.data.web.PageableDefault(size = 50) org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<CustomerAddress> addresses = addressRepository.findAll(pageable);
        org.springframework.data.domain.Page<CustomerAddressDto> dtos = addresses.map(this::toDto);
        return ResponseEntity.ok(ApiResponse.success(dtos, "All customer addresses retrieved"));
    }

    private CustomerAddressDto toDto(CustomerAddress entity) {
        return CustomerAddressDto.builder().id(entity.getId()).customerId(entity.getCustomerId()).label(entity.getLabel()).addressLine1(entity.getAddressLine1()).addressLine2(entity.getAddressLine2()).city(entity.getCity()).state(entity.getState()).zipCode(entity.getZipCode()).latitude(entity.getLatitude()).longitude(entity.getLongitude()).isDefault(entity.getIsDefault()).build();
    }

    
    public AdminCustomerController(final CustomerAddressRepository addressRepository, final ICustomerRepository customerRepository) {
        this.addressRepository = addressRepository;
        this.customerRepository = customerRepository;
    }
}
