package com.fooddelivery.customer.controller;

import com.fooddelivery.common.client.*;
import com.fooddelivery.common.dto.organisation.MembershipDto;
import com.fooddelivery.common.dto.restaurant.OutletOrganisationDto;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.security.money.*;
import com.fooddelivery.common.security.organisation.DefaultOrganisationAccessPolicy;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.customer.service.money.RestaurantSummaryService;
import com.fooddelivery.money.controller.RestaurantMoneyController;
import com.fooddelivery.order.repository.*;
import com.fooddelivery.order.security.OrderSecurityHelper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP handlers with real method advice and the shared permission matrix, never a mocked grant. */
@SpringJUnitConfig(RestaurantRefundPermissionTest.Config.class)
class RestaurantRefundPermissionTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean IOrderRepository orders() { return mock(IOrderRepository.class); }
        @Bean RefundRepository refunds() { return mock(RefundRepository.class); }
        @Bean RestaurantSummaryService summaries() { return mock(RestaurantSummaryService.class); }
        @Bean LedgerClient ledger() { return mock(LedgerClient.class); }
        @Bean RestaurantServiceClient restaurants() { return mock(RestaurantServiceClient.class); }
        @Bean OrganisationServiceClient memberships() { return mock(OrganisationServiceClient.class); }
        @Bean DefaultOrganisationAccessPolicy organisationAccessPolicy(OrganisationServiceClient c) {
            return new DefaultOrganisationAccessPolicy(c,new SimpleMeterRegistry());
        }
        @Bean MoneyAccessPolicy moneyAccessPolicy(RestaurantServiceClient r,DefaultOrganisationAccessPolicy a) {
            return new DefaultMoneyAccessPolicy(r,mock(CampaignServiceClient.class),a);
        }
        @Bean OrderSecurityHelper orderSecurityHelper(IOrderRepository o,RestaurantServiceClient r,DefaultOrganisationAccessPolicy a) {
            return new OrderSecurityHelper(o,r,a);
        }
        @Bean RestaurantMoneyController controller(IOrderRepository o,MoneyAccessPolicy a,RefundRepository r,
                RestaurantSummaryService s,LedgerClient l) { return new RestaurantMoneyController(o,a,r,s,l); }
    }
    @Autowired RestaurantMoneyController controller;
    @Autowired IOrderRepository orders;
    @Autowired RefundRepository refunds;
    @Autowired RestaurantSummaryService summaries;
    @Autowired LedgerClient ledger;
    @Autowired RestaurantServiceClient restaurants;
    @Autowired OrganisationServiceClient memberships;
    MockMvc http;
    UUID outlet,org,user;
    String path;
    @BeforeEach void setup() {
        reset(orders,refunds,summaries,ledger,restaurants,memberships);
        outlet=UUID.randomUUID();org=UUID.randomUUID();user=UUID.randomUUID();
        path="/api/v1/money/restaurant/"+outlet;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.toString(),null,
                List.of(new SimpleGrantedAuthority("ROLE_RESTAURANT"))));
        when(restaurants.getOutletOrganisation(outlet)).thenReturn(new OutletOrganisationDto(outlet,UUID.randomUUID(),org));
        http=MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.fooddelivery.common.exception.GlobalExceptionHandler()).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void member(OrganisationRole role,MembershipStatus status) {
        when(memberships.getMembership(org,user)).thenReturn(new MembershipDto(org,OrganisationStatus.ACTIVE,user,role,status));
    }
    @Test void staffCanReadPendingOperationsWhileFinancialReadsStayForbidden() throws Exception {
        member(OrganisationRole.STAFF,MembershipStatus.ACTIVE);
        when(refunds.findRestaurantFaultRefunds(outlet,RefundStatus.PROCESSING)).thenReturn(List.of());
        http.perform(get(path+"/refund-requests")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        clearInvocations(refunds);
        for(String suffix:List.of("/summary","/statement","/refunds","/orders/"+UUID.randomUUID())) {
            http.perform(get(path+suffix)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(refunds,summaries,ledger,orders);
    }
    @Test void anOwnerCanAlsoReadThePendingOperationalQueue() throws Exception {
        member(OrganisationRole.OWNER,MembershipStatus.ACTIVE);
        http.perform(get(path+"/refund-requests")).andExpect(status().isOk());
        verify(refunds).findRestaurantFaultRefunds(outlet,RefundStatus.PROCESSING);
    }
    @Test void aRemovedOrUnrelatedRestaurantUserCannotReadTheQueue() throws Exception {
        member(OrganisationRole.STAFF,MembershipStatus.REMOVED);
        http.perform(get(path+"/refund-requests")).andExpect(status().isForbidden());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(),null,
                List.of(new SimpleGrantedAuthority("ROLE_RESTAURANT"))));
        http.perform(get(path+"/refund-requests")).andExpect(status().isForbidden());
        verifyNoInteractions(refunds);
    }
    @Test void anUnavailableOrMismatchedOutletCannotGrantQueueAccess() throws Exception {
        when(restaurants.getOutletOrganisation(outlet)).thenThrow(new IllegalStateException("Unavailable"));
        http.perform(get(path+"/refund-requests")).andExpect(status().isForbidden());
        doReturn(new OutletOrganisationDto(UUID.randomUUID(),UUID.randomUUID(),org)).when(restaurants).getOutletOrganisation(outlet);
        http.perform(get(path+"/refund-requests")).andExpect(status().isForbidden());
        verifyNoInteractions(refunds,memberships);
    }
}
