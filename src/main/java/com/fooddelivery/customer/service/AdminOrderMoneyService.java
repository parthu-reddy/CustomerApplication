package com.fooddelivery.customer.service;

import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.customer.dto.AdminOrderMoney;
import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOrderMoneyService {

    private final IOrderRepository orderRepository;
    private final LedgerClient ledgerClient;

    public AdminOrderMoney getOrderMoney(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        AdminOrderMoney money = new AdminOrderMoney();
        money.setOrderId(order.getId());
        money.setTotalAmount(order.getTotalAmount());

        BigDecimal itemTotal = BigDecimal.ZERO;
        if (order.getOrderItems() != null) {
            itemTotal = order.getOrderItems().stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        money.setFoodCost(itemTotal);
        money.setDeliveryFee(order.getDeliveryFee());
        money.setCustomerPlatformFee(order.getCustomerPlatformFee());

        money.setRestaurantPayout(order.getRestaurantPayout());
        money.setRestaurantPlatformFee(order.getRestaurantPlatformFee());
        money.setRestaurantDeliveryContribution(order.getRestaurantDeliveryContribution());

        money.setDriverGrossPayout(order.getDriverGrossPayout());
        money.setDriverTaxes(order.getDriverTaxes());
        money.setDriverNetPayout(order.getDriverNetPayout());
        money.setPlatformBonus(order.getPlatformBonus());

        money.setSgst(order.getSgst());
        money.setCgst(order.getCgst());

        List<LedgerStatementLineDto> lines = ledgerClient.getStatementByReference(orderId);
        money.setLedgerLines(lines);

        return money;
    }
}
