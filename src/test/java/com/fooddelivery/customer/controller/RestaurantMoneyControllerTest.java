package com.fooddelivery.customer.controller;

import com.fooddelivery.money.controller.RestaurantMoneyController;

import com.fooddelivery.common.security.money.MoneyAccessPolicy;
import com.fooddelivery.common.security.money.MoneyOwnerType;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class RestaurantMoneyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private MoneyAccessPolicy moneyAccessPolicy;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private Order testOrder;

    @BeforeEach
    void setUp() {
        RestaurantMoneyController controller = new RestaurantMoneyController(orderRepository, moneyAccessPolicy, Mockito.mock(RefundRepository.class), org.mockito.Mockito.mock(com.fooddelivery.customer.service.money.RestaurantSummaryService.class), org.mockito.Mockito.mock(com.fooddelivery.customer.client.LedgerClient.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        testOrder = new Order();
        testOrder.setId(orderId);
        testOrder.setRestaurantId(restaurantId);
        testOrder.setRestaurantPayout(BigDecimal.valueOf(85));
        testOrder.setRestaurantPlatformFee(BigDecimal.valueOf(10));
        testOrder.setRestaurantDeliveryContribution(BigDecimal.valueOf(5));
        
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setMenuItemId(UUID.randomUUID());
        item.setQuantity(2);
        item.setPrice(BigDecimal.valueOf(42.5));
        testOrder.setOrderItems(Set.of(item));
    }

    @Test
    void getOrderEarnings_ReturnsEarnings_WhenOrderBelongsToOutlet() throws Exception {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        mockMvc.perform(get("/api/v1/money/restaurant/" + restaurantId + "/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.netPayout").value(85));
    }

    /** Another outlet's order is not this outlet's business. */
    @Test
    void getOrderEarnings_IsNotFound_WhenTheOrderBelongsToAnotherOutlet() throws Exception {
        testOrder.setRestaurantId(UUID.randomUUID());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        mockMvc.perform(get("/api/v1/money/restaurant/" + restaurantId + "/orders/" + orderId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderEarnings_IsNotFound_WhenTheOrderDoesNotExist() throws Exception {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/money/restaurant/" + restaurantId + "/orders/" + orderId))
                .andExpect(status().isNotFound());
    }

    /** The deductions are shown, not just the net: an outlet must see why it was paid that. */
    @Test
    void getOrderEarnings_ShowsTheDeductionsBehindTheNet() throws Exception {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        mockMvc.perform(get("/api/v1/money/restaurant/" + restaurantId + "/orders/" + orderId))
                .andExpect(status().isOk())
                // Rupees as the API stores them, not paise.
                .andExpect(jsonPath("$.platformFee").value(10))
                .andExpect(jsonPath("$.deliveryContribution").value(5));
    }
}
