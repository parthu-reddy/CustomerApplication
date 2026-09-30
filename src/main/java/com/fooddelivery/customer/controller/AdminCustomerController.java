package com.fooddelivery.customer.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.customer.dto.CustomerAddressDto;
import com.fooddelivery.customer.entity.CustomerAddress;
import com.fooddelivery.customer.repository.CustomerAddressRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.stream.Collectors;
import com.fooddelivery.customer.repository.ICustomerRepository;
import com.fooddelivery.customer.entity.Customer;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/internal/admin/customers")
@Validated
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class AdminCustomerController {
    

    private final CustomerAddressRepository addressRepository;
    private final ICustomerRepository customerRepository;

    @Value("${platform.fleet.allowed-city-ids:BLR}")
    private String allowedFleetCityIds = "BLR";

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<com.fooddelivery.common.dto.PageResponseDto<CustomerAddressDto>>> getAllCustomerAddresses(
            @RequestParam(required = false) @com.fooddelivery.common.location.CityId @Pattern(regexp = com.fooddelivery.common.location.CityIdValidator.REGEX) String cityId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size) {
        cityId = com.fooddelivery.common.location.FleetCityScope.resolve(cityId, allowedFleetCityIds);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by("id").ascending());
        org.springframework.data.domain.Page<CustomerAddress> addresses = addressRepository.findByCityId(cityId, pageable);
        org.springframework.data.domain.Page<CustomerAddressDto> dtos = addresses.map(this::toDto);
        return ResponseEntity.ok(ApiResponse.success(com.fooddelivery.common.dto.PageResponseDto.of(dtos), "All customer addresses retrieved"));
    }

    private CustomerAddressDto toDto(CustomerAddress entity) {
        return CustomerAddressDto.builder().id(entity.getId()).customerId(entity.getCustomerId()).label(entity.getLabel()).addressLine1(entity.getAddressLine1()).addressLine2(entity.getAddressLine2()).city(entity.getCity()).cityId(entity.getCityId()).state(entity.getState()).zipCode(entity.getZipCode()).latitude(entity.getLatitude()).longitude(entity.getLongitude()).isDefault(entity.getIsDefault()).build();
    }

    
}
