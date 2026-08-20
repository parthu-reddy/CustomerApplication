package com.fooddelivery.customer;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import com.fooddelivery.order.controller.InternalOrderController;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.service.OrderSagaOrchestrator;
import com.fooddelivery.order.service.OrderRefundService;

public abstract class ContractTestBase {

    @BeforeEach
    public void setup() {
        IOrderRepository orderRepository = Mockito.mock(IOrderRepository.class);
        OrderSagaOrchestrator orderSagaOrchestrator = Mockito.mock(OrderSagaOrchestrator.class);
        OrderRefundService orderRefundService = Mockito.mock(OrderRefundService.class);

        // Two driver ids are in play: the all-zeros one (contracts asserting an EMPTY page) and
        // 123e4567... (contracts asserting a POPULATED page). Stubbing per id lets both hold, rather
        // than deleting one of each pair.
        final java.util.UUID EMPTY_DRIVER = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");
        final java.util.UUID SAMPLE_ID = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        final java.util.UUID PARTICIPANT_2 = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

        // Each contract asserts a different status on the same order id, so each list gets its own
        // fixture: OUT_FOR_DELIVERY for active, DELIVERED for history, PREPARING for unassigned.
        java.util.function.Function<com.fooddelivery.common.enums.OrderStatus, com.fooddelivery.order.entity.Order> mkOrder =
                st -> {
                    com.fooddelivery.order.entity.Order o = new com.fooddelivery.order.entity.Order();
                    o.setId(SAMPLE_ID);
                    o.setCustomerId(SAMPLE_ID);
                    o.setRestaurantId(PARTICIPANT_2);
                    o.setTotalAmount(new java.math.BigDecimal("100.0"));
                    o.setStatus(st);
                    return o;
                };
        com.fooddelivery.order.entity.Order order = mkOrder.apply(com.fooddelivery.common.enums.OrderStatus.PREPARING);
        com.fooddelivery.order.entity.Order activeOrder = mkOrder.apply(com.fooddelivery.common.enums.OrderStatus.HANDED_OVER);
        activeOrder.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.OUT_FOR_DELIVERY);
        com.fooddelivery.order.entity.Order historyOrder = mkOrder.apply(com.fooddelivery.common.enums.OrderStatus.HANDED_OVER);
        historyOrder.setDeliveryStatus(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED);

        // A PageImpl built from a bare list is UNPAGED; serialising it calls getPageNumber() on
        // Pageable.unpaged(), which throws UnsupportedOperationException and yields a 500.
        org.springframework.data.domain.Pageable page0 = org.springframework.data.domain.PageRequest.of(0, 20);
        org.springframework.data.domain.Page<com.fooddelivery.order.entity.Order> activePage =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(activeOrder), page0, 1);
        org.springframework.data.domain.Page<com.fooddelivery.order.entity.Order> historyPage =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(historyOrder), page0, 1);
        org.springframework.data.domain.Page<com.fooddelivery.order.entity.Order> emptyPage =
                new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList(), page0, 0);
        Mockito.when(orderRepository.findByStatusInAndDeliveryExecutiveIdIsNull(Mockito.anyList(), Mockito.any(org.springframework.data.domain.Pageable.class)))
               .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(order), page0, 1));
        Mockito.when(orderRepository.findActiveOrdersForDriver(Mockito.any(), Mockito.anyList(), Mockito.anyList(), Mockito.any(org.springframework.data.domain.Pageable.class)))
               .thenReturn(activePage);
        Mockito.when(orderRepository.findActiveOrdersForDriver(Mockito.eq(EMPTY_DRIVER), Mockito.anyList(), Mockito.anyList(), Mockito.any(org.springframework.data.domain.Pageable.class)))
               .thenReturn(emptyPage);
        Mockito.when(orderRepository.findHistoryOrdersForDriver(Mockito.any(), Mockito.anyList(), Mockito.anyList(), Mockito.any(), Mockito.any(), Mockito.any(org.springframework.data.domain.Pageable.class)))
               .thenReturn(historyPage);
        Mockito.when(orderRepository.findHistoryOrdersForDriver(Mockito.eq(EMPTY_DRIVER), Mockito.anyList(), Mockito.anyList(), Mockito.any(), Mockito.any(), Mockito.any(org.springframework.data.domain.Pageable.class)))
               .thenReturn(emptyPage);
        // getOrderParticipants and partialRefund both 404 on an absent order.
        Mockito.when(orderRepository.findById(Mockito.any(java.util.UUID.class)))
               .thenReturn(java.util.Optional.of(order));

        InternalOrderController internalOrderController = new InternalOrderController(
                orderRepository,
                orderSagaOrchestrator,
                orderRefundService
        );

        // Standalone MockMvc registers NO custom argument resolvers, so a handler taking Pageable
        // fails with "No primary or single unique constructor found for interface ...Pageable".
        // In a full Spring context PageableHandlerMethodArgumentResolver is auto-registered.
        // CustomerApplication has TWO classes named InternalOrderController, both mapped to
        // /api/v1/internal/orders: com.fooddelivery.order.controller (drivers, participants, refund)
        // and com.fooddelivery.customer.controller (invoice). Both must be mounted.
        com.fooddelivery.customer.service.CustomerOrderService customerOrderService =
                Mockito.mock(com.fooddelivery.customer.service.CustomerOrderService.class);
        com.fooddelivery.customer.controller.OrderController orderController =
                Mockito.mock(com.fooddelivery.customer.controller.OrderController.class);
        Mockito.when(customerOrderService.getOrderById(Mockito.any(java.util.UUID.class))).thenReturn(order);
        com.fooddelivery.customer.dto.OrderResponse invoiceResponse = new com.fooddelivery.customer.dto.OrderResponse();
        invoiceResponse.setId(SAMPLE_ID);
        invoiceResponse.setTotalAmount(new java.math.BigDecimal("100.0"));
        Mockito.when(orderController.mapToResponse(Mockito.any())).thenReturn(invoiceResponse);
        com.fooddelivery.customer.controller.InternalOrderController invoiceController =
                new com.fooddelivery.customer.controller.InternalOrderController(orderController, customerOrderService);

        RestAssuredMockMvc.standaloneSetup(
                org.springframework.test.web.servlet.setup.MockMvcBuilders
                        .standaloneSetup(internalOrderController, invoiceController)
                        .setCustomArgumentResolvers(
                                new org.springframework.data.web.PageableHandlerMethodArgumentResolver()));
    }
}
