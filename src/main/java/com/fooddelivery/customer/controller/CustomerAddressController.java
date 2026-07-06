package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.dto.AddressRequest;
import com.fooddelivery.customer.dto.CustomerAddressDto;
import com.fooddelivery.customer.entity.CustomerAddress;
import com.fooddelivery.customer.repository.CustomerAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/addresses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER') and #customerId.toString() == authentication.principal")
public class CustomerAddressController {

    private final CustomerAddressRepository addressRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerAddressDto>> addAddress(
            @PathVariable UUID customerId,
            @Valid @RequestBody AddressRequest request) {
        
        CustomerAddress address = CustomerAddress.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .label(request.getLabel())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
                
        CustomerAddress saved = addressRepository.save(address);
        return ResponseEntity.ok(ApiResponse.success(toDto(saved), "Address added successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerAddressDto>>> getAddresses(@PathVariable UUID customerId) {
        
        List<CustomerAddress> addresses = addressRepository.findByCustomerId(customerId);
        List<CustomerAddressDto> dtos = addresses.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Addresses retrieved"));
    }

    private CustomerAddressDto toDto(CustomerAddress entity) {
        return CustomerAddressDto.builder()
                .id(entity.getId())
                .customerId(entity.getCustomerId())
                .label(entity.getLabel())
                .addressLine1(entity.getAddressLine1())
                .addressLine2(entity.getAddressLine2())
                .city(entity.getCity())
                .state(entity.getState())
                .zipCode(entity.getZipCode())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .isDefault(entity.getIsDefault())
                .build();
    }
}
