package com.fooddelivery.order.controller;

import com.fooddelivery.common.client.RestaurantServiceClient;
import com.fooddelivery.common.dto.order.OrderReviewAuthorizationRequest;
import com.fooddelivery.common.dto.order.OrderReviewAuthorizationResult;
import com.fooddelivery.common.dto.order.OrderReviewTargetAuthorizationRequest;
import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.common.enums.ReviewEntityType;
import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.repository.IOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class InternalOrderReviewControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CUSTOMER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID RESTAURANT_OWNER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID OUTLET_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID DRIVER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID PRODUCT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Mock private IOrderRepository orderRepository;
    @Mock private RestaurantServiceClient restaurantServiceClient;

    private InternalOrderReviewController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalOrderReviewController(orderRepository, restaurantServiceClient);
        lenient().when(orderRepository.findForReviewAuthorization(ORDER_ID))
                .thenReturn(Optional.of(deliveredOrder()));
    }

    @Test
    void customerMayReviewTheRestaurantDriverAndDishButNotThemselfOrAnUnrelatedDish() {
        List<OrderReviewAuthorizationResult> results = authorize(CUSTOMER_ID, RoleName.CUSTOMER,
                target(ReviewEntityType.RESTAURANT, OUTLET_ID),
                target(ReviewEntityType.DRIVER, DRIVER_ID),
                target(ReviewEntityType.PRODUCT, PRODUCT_ID),
                target(ReviewEntityType.CUSTOMER, CUSTOMER_ID),
                target(ReviewEntityType.PRODUCT, UUID.randomUUID()));

        assertThat(results).extracting(OrderReviewAuthorizationResult::isAllowed)
                .containsExactly(true, true, true, false, false);
        assertThat(results).extracting(OrderReviewAuthorizationResult::getReasonCode)
                .containsExactly(null, null, null, "SELF_REVIEW", "TARGET_NOT_ON_ORDER");
        verifyNoInteractions(restaurantServiceClient);
    }

    @Test
    void restaurantOwnerMayReviewTheCustomerAndDriverButNoOtherTargetType() {
        when(restaurantServiceClient.getOwnerOutlets(RESTAURANT_OWNER_ID.toString(), "customer-service"))
                .thenReturn(List.of(OUTLET_ID.toString()));

        List<OrderReviewAuthorizationResult> results = authorize(RESTAURANT_OWNER_ID, RoleName.RESTAURANT,
                target(ReviewEntityType.CUSTOMER, CUSTOMER_ID),
                target(ReviewEntityType.DRIVER, DRIVER_ID),
                target(ReviewEntityType.RESTAURANT, OUTLET_ID),
                target(ReviewEntityType.PRODUCT, PRODUCT_ID));

        assertThat(results).extracting(OrderReviewAuthorizationResult::isAllowed)
                .containsExactly(true, true, false, false);
        assertThat(results).extracting(OrderReviewAuthorizationResult::getReasonCode)
                .containsExactly(null, null, "ROLE_TARGET_NOT_ALLOWED", "ROLE_TARGET_NOT_ALLOWED");
        verify(restaurantServiceClient).getOwnerOutlets(RESTAURANT_OWNER_ID.toString(), "customer-service");
    }

    @Test
    void restaurantUserWhoDoesNotOwnTheOrderOutletIsNotAParticipant() {
        UUID unrelatedOwner = UUID.randomUUID();
        when(restaurantServiceClient.getOwnerOutlets(unrelatedOwner.toString(), "customer-service"))
                .thenReturn(List.of(UUID.randomUUID().toString()));

        List<OrderReviewAuthorizationResult> results = authorize(unrelatedOwner, RoleName.RESTAURANT,
                target(ReviewEntityType.CUSTOMER, CUSTOMER_ID));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getReasonCode()).isEqualTo("ACTOR_NOT_PARTICIPANT");
        });
    }

    @Test
    void deliveryPartnerMayReviewTheCustomerAndRestaurantButNotThemselfOrAProduct() {
        List<OrderReviewAuthorizationResult> results = authorize(DRIVER_ID, RoleName.DELIVERY,
                target(ReviewEntityType.CUSTOMER, CUSTOMER_ID),
                target(ReviewEntityType.RESTAURANT, OUTLET_ID),
                target(ReviewEntityType.DRIVER, DRIVER_ID),
                target(ReviewEntityType.PRODUCT, PRODUCT_ID));

        assertThat(results).extracting(OrderReviewAuthorizationResult::isAllowed)
                .containsExactly(true, true, false, false);
        assertThat(results).extracting(OrderReviewAuthorizationResult::getReasonCode)
                .containsExactly(null, null, "SELF_REVIEW", "ROLE_TARGET_NOT_ALLOWED");
        verifyNoInteractions(restaurantServiceClient);
    }

    @Test
    void onlyDeliveredOrdersAndTheirParticipantsCanBeAuthorized() {
        Order notDelivered = deliveredOrder();
        notDelivered.setDeliveryStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        when(orderRepository.findForReviewAuthorization(ORDER_ID)).thenReturn(Optional.of(notDelivered));

        List<OrderReviewAuthorizationResult> beforeDelivery = authorize(CUSTOMER_ID, RoleName.CUSTOMER,
                target(ReviewEntityType.RESTAURANT, OUTLET_ID));
        assertThat(beforeDelivery).singleElement().satisfies(result -> {
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getReasonCode()).isEqualTo("ORDER_NOT_DELIVERED");
        });

        when(orderRepository.findForReviewAuthorization(ORDER_ID)).thenReturn(Optional.of(deliveredOrder()));
        List<OrderReviewAuthorizationResult> unrelatedActor = authorize(UUID.randomUUID(), RoleName.CUSTOMER,
                target(ReviewEntityType.RESTAURANT, OUTLET_ID));
        assertThat(unrelatedActor).singleElement().satisfies(result -> {
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getReasonCode()).isEqualTo("ACTOR_NOT_PARTICIPANT");
        });
    }

    @Test
    void anUnknownOrderReturnsNotFound() {
        when(orderRepository.findForReviewAuthorization(ORDER_ID)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.authorizeTargets(ORDER_ID,
                request(CUSTOMER_ID, RoleName.CUSTOMER, target(ReviewEntityType.RESTAURANT, OUTLET_ID)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private List<OrderReviewAuthorizationResult> authorize(
            UUID reviewerId, RoleName role, OrderReviewTargetAuthorizationRequest... targets) {
        return controller.authorizeTargets(ORDER_ID, request(reviewerId, role, targets))
                .getBody().getData();
    }

    private OrderReviewAuthorizationRequest request(
            UUID reviewerId, RoleName role, OrderReviewTargetAuthorizationRequest... targets) {
        return OrderReviewAuthorizationRequest.builder()
                .reviewerId(reviewerId)
                .reviewerRole(role)
                .targets(List.of(targets))
                .build();
    }

    private OrderReviewTargetAuthorizationRequest target(ReviewEntityType type, UUID targetId) {
        return OrderReviewTargetAuthorizationRequest.builder()
                .targetType(type)
                .targetId(targetId.toString())
                .build();
    }

    private Order deliveredOrder() {
        OrderItem dish = OrderItem.builder().menuItemId(PRODUCT_ID).name("Test dish").build();
        return Order.builder()
                .id(ORDER_ID)
                .customerId(CUSTOMER_ID)
                .restaurantId(OUTLET_ID)
                .deliveryExecutiveId(DRIVER_ID)
                .deliveryStatus(DeliveryStatus.DELIVERED)
                .orderItems(Set.of(dish))
                .build();
    }
}
