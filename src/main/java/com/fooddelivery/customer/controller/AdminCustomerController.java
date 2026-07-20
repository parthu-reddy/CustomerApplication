package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.dto.CustomerAddressDto;
import com.fooddelivery.customer.entity.CustomerAddress;
import com.fooddelivery.customer.repository.CustomerAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/internal/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerAddressRepository addressRepository;

    @GetMapping("/addresses")
    // @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CustomerAddressDto>>> getAllCustomerAddresses() {
        List<CustomerAddress> addresses = addressRepository.findAll();
        List<CustomerAddressDto> dtos = addresses.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "All customer addresses retrieved"));
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
