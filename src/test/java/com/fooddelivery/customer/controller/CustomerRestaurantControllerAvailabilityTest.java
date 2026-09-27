package com.fooddelivery.customer.controller;

import com.fooddelivery.common.client.MapsServiceClient;
import com.fooddelivery.common.dto.maps.FleetAvailabilityResponseDto;
import com.fooddelivery.customer.client.AdvertisementClient;
import com.fooddelivery.customer.client.RestaurantClient;
import com.fooddelivery.customer.config.DeliveryZoneConfig;
import com.fooddelivery.customer.config.DynamicPricingConfig;
import com.fooddelivery.customer.exception.DeliveryPartnerUnavailableException;
import com.fooddelivery.customer.repository.CustomerAddressRepository;
import com.fooddelivery.customer.service.DynamicPricingService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerRestaurantControllerAvailabilityTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    private final RestaurantClient restaurants = mock(RestaurantClient.class);
    private final MapsServiceClient maps = mock(MapsServiceClient.class);
    private final DeliveryZoneConfig zone = new DeliveryZoneConfig();
    private final CustomerRestaurantController controller = new CustomerRestaurantController(
            restaurants,
            maps,
            mock(DynamicPricingService.class),
            mock(DynamicPricingConfig.class),
            mock(CustomerAddressRepository.class),
            mock(AdvertisementClient.class),
            zone,
            mock(org.springframework.data.redis.core.StringRedisTemplate.class));

    CustomerRestaurantControllerAvailabilityTest() {
        when(restaurants.getRestaurantById(RESTAURANT_ID)).thenReturn(Map.of(
                "data", Map.of("lat", 12.98, "lng", 77.65)));
    }

    @Test
    void reportsBusinessUnavailabilityOnlyWhenMapsAnswersUnavailable() {
        when(maps.checkFleetAvailability(zone.getDefaultCity(), 12.98, 77.65,
                zone.getFleetSearchRadiusKm()))
                .thenReturn(FleetAvailabilityResponseDto.builder().available(false).build());

        assertThrows(DeliveryPartnerUnavailableException.class,
                () -> controller.checkDeliveryAvailability(RESTAURANT_ID));
    }

    @Test
    void propagatesMapsServiceFailureInsteadOfRelabelingItAsNoNearbyPartner() {
        ResponseStatusException upstreamFailure = new ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Fleet availability is temporarily unavailable");
        when(maps.checkFleetAvailability(zone.getDefaultCity(), 12.98, 77.65,
                zone.getFleetSearchRadiusKm())).thenThrow(upstreamFailure);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> controller.checkDeliveryAvailability(RESTAURANT_ID));

        assertSame(upstreamFailure, thrown);
    }
}
