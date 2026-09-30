package com.fooddelivery.customer.controller;

import com.fooddelivery.customer.entity.CustomerAddress;
import com.fooddelivery.customer.repository.CustomerAddressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminCustomerControllerFleetScopeTest {

    @Test
    void returnsOnlyTheRequestedFleetCityPage() {
        CustomerAddressRepository addresses = mock(CustomerAddressRepository.class);
        CustomerAddress address = CustomerAddress.builder().id(UUID.randomUUID()).customerId(UUID.randomUUID())
                .label("Home").addressLine1("1 Main St").city("Bengaluru").cityId("BLR")
                .state("Karnataka").zipCode("560001").latitude(12.97).longitude(77.59).build();
        when(addresses.findByCityId(eq("BLR"), any())).thenReturn(new PageImpl<>(List.of(address), PageRequest.of(0, 100), 1));

        AdminCustomerController controller = new AdminCustomerController(addresses, mock(com.fooddelivery.customer.repository.ICustomerRepository.class));

        var response = controller.getAllCustomerAddresses("BLR", 0, 100);

        assertThat(response.getBody().getData().getContent()).extracting(com.fooddelivery.customer.dto.CustomerAddressDto::getCityId)
                .containsExactly("BLR");
        verify(addresses).findByCityId(eq("BLR"), any());
    }

    @Test
    void rejectsUnknownOrMalformedCityBeforeQueryingAddresses() {
        CustomerAddressRepository addresses = mock(CustomerAddressRepository.class);
        AdminCustomerController controller = new AdminCustomerController(addresses, mock(com.fooddelivery.customer.repository.ICustomerRepository.class));

        assertThatThrownBy(() -> controller.getAllCustomerAddresses("NYC", 0, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not an enabled");
        assertThatThrownBy(() -> controller.getAllCustomerAddresses("BLR/redis", 0, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("canonical");
        verifyNoInteractions(addresses);
    }

    @Test
    void resolvesTheOnlyConfiguredCityForALegacyMapCall() {
        CustomerAddressRepository addresses = mock(CustomerAddressRepository.class);
        when(addresses.findByCityId(eq("BLR"), any())).thenReturn(org.springframework.data.domain.Page.empty());
        AdminCustomerController controller = new AdminCustomerController(addresses, mock(com.fooddelivery.customer.repository.ICustomerRepository.class));

        controller.getAllCustomerAddresses(null, 0, 100);

        verify(addresses).findByCityId(eq("BLR"), any());
    }
}
