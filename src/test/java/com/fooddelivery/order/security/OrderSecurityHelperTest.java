package com.fooddelivery.order.security;

import com.fooddelivery.common.client.*;
import com.fooddelivery.common.dto.organisation.MembershipDto;
import com.fooddelivery.common.dto.restaurant.OutletOrganisationDto;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.security.organisation.DefaultOrganisationAccessPolicy;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderSecurityHelperTest {
    private final IOrderRepository orders=mock(IOrderRepository.class);
    private final RestaurantServiceClient restaurants=mock(RestaurantServiceClient.class);
    private final OrganisationServiceClient memberships=mock(OrganisationServiceClient.class);
    private final OrderSecurityHelper access=new OrderSecurityHelper(orders,restaurants,
        new DefaultOrganisationAccessPolicy(memberships,new SimpleMeterRegistry()));
    private final UUID orderId=UUID.randomUUID(),outlet=UUID.randomUUID(),org=UUID.randomUUID(),staff=UUID.randomUUID();

    private Order order() {
        Order order=new Order();order.setId(orderId);order.setCustomerId(UUID.randomUUID());
        order.setDeliveryExecutiveId(UUID.randomUUID());order.setRestaurantId(outlet);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));return order;
    }
    @Test void staffCanOperateOutletOrdersWithoutAcquiringCustomerReviewAccess() {
        order();when(restaurants.getOutletOrganisation(outlet)).thenReturn(new OutletOrganisationDto(outlet,UUID.randomUUID(),org));
        when(memberships.getMembership(org,staff)).thenReturn(new MembershipDto(org,OrganisationStatus.ACTIVE,
            staff,OrganisationRole.STAFF,MembershipStatus.ACTIVE));
        assertTrue(access.isOrderParticipant(orderId,staff.toString()));
        assertFalse(access.isOrderCustomer(orderId,staff.toString()));
        verify(memberships).getMembership(org,staff);
    }
    @Test void removedOrUnrelatedMembersCannotOperateOrders() {
        order();when(restaurants.getOutletOrganisation(outlet)).thenReturn(new OutletOrganisationDto(outlet,UUID.randomUUID(),org));
        when(memberships.getMembership(org,staff)).thenReturn(new MembershipDto(org,OrganisationStatus.ACTIVE,
            staff,OrganisationRole.OWNER,MembershipStatus.REMOVED));
        assertFalse(access.isOrderParticipant(orderId,staff.toString()));
        assertFalse(access.isOrderParticipant(orderId,UUID.randomUUID().toString()));
    }
    @Test void aMismatchedOutletOrUnavailableRestaurantCannotGrantOrderAccess() {
        order();when(restaurants.getOutletOrganisation(outlet)).thenReturn(new OutletOrganisationDto(UUID.randomUUID(),UUID.randomUUID(),org));
        assertFalse(access.isOrderParticipant(orderId,staff.toString()));verifyNoInteractions(memberships);
        when(restaurants.getOutletOrganisation(outlet)).thenThrow(new IllegalStateException("Unavailable"));
        assertFalse(access.isOrderParticipant(orderId,staff.toString()));
    }
    @Test void theCustomerAndAssignedDriverRemainParticipantsWithoutMembershipLookup() {
        var order=order();assertTrue(access.isOrderParticipant(orderId,order.getCustomerId().toString()));
        assertTrue(access.isOrderParticipant(orderId,order.getDeliveryExecutiveId().toString()));
        assertTrue(access.isOrderCustomer(orderId,order.getCustomerId().toString()));
        assertFalse(access.isOrderCustomer(orderId,order.getDeliveryExecutiveId().toString()));
        verifyNoInteractions(restaurants,memberships);
    }
}
