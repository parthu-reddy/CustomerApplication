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

        org.springframework.data.domain.Page<com.fooddelivery.order.entity.Order> emptyPage = new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList());
        Mockito.when(orderRepository.findByStatusInAndDeliveryExecutiveIdIsNull(Mockito.anyList(), Mockito.any(org.springframework.data.domain.Pageable.class)))
               .thenReturn(emptyPage);
        Mockito.when(orderRepository.findActiveOrdersForDriver(Mockito.any(), Mockito.anyList(), Mockito.anyList(), Mockito.any(org.springframework.data.domain.Pageable.class)))
               .thenReturn(emptyPage);
        Mockito.when(orderRepository.findHistoryOrdersForDriver(Mockito.any(), Mockito.anyList(), Mockito.anyList(), Mockito.any(), Mockito.any(), Mockito.any(org.springframework.data.domain.Pageable.class)))
               .thenReturn(emptyPage);
        Mockito.when(orderRepository.findById(Mockito.any(java.util.UUID.class)))
               .thenReturn(java.util.Optional.empty());

        InternalOrderController internalOrderController = new InternalOrderController(
                orderRepository,
                orderSagaOrchestrator,
                orderRefundService
        );

        RestAssuredMockMvc.standaloneSetup(internalOrderController);
    }
}
