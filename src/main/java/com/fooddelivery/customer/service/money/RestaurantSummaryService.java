package com.fooddelivery.customer.service.money;

import com.fooddelivery.customer.dto.RestaurantSummary;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.customer.client.LedgerClient;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@lombok.RequiredArgsConstructor
public class RestaurantSummaryService {
    private final IOrderRepository orderRepository;
    private final LedgerClient ledgerClient;
    private final ObjectMapper objectMapper;

    public RestaurantSummary getSummary(UUID outletId, String period) {
        RestaurantSummary summary = new RestaurantSummary();
        
        // Mock date ranges for the period
        java.time.LocalDateTime start = java.time.LocalDateTime.now().minusMonths(1);
        java.time.LocalDateTime end = java.time.LocalDateTime.now().plusDays(1);
        
        List<Order> orders = orderRepository.findByRestaurantId(outletId).stream()
                .filter(o -> o.getCreatedAt().isAfter(start) && o.getCreatedAt().isBefore(end))
                .collect(java.util.stream.Collectors.toList());
        summary.setOrders(orders.size());
        
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal platformFees = BigDecimal.ZERO;
        BigDecimal deliveryContrib = BigDecimal.ZERO;
        BigDecimal netEarnings = BigDecimal.ZERO;
        
        for (Order o : orders) {
            if (o.getOrderItems() != null) {
                gross = gross.add(o.getOrderItems().stream()
                    .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            if (o.getRestaurantPlatformFee() != null) platformFees = platformFees.add(o.getRestaurantPlatformFee());
            if (o.getRestaurantDeliveryContribution() != null) deliveryContrib = deliveryContrib.add(o.getRestaurantDeliveryContribution());
            if (o.getRestaurantPayout() != null) netEarnings = netEarnings.add(o.getRestaurantPayout());
        }
        
        summary.setGrossFoodCost(gross);
        summary.setPlatformFees(platformFees);
        summary.setDeliveryContribution(deliveryContrib);
        summary.setNetEarnings(netEarnings);
        summary.setPlatformBonus(BigDecimal.ZERO);
        summary.setClawbacks(BigDecimal.ZERO);
        
        try {
            JsonNode pending = ledgerClient.getPendingPayouts(0, 100);
            summary.setPendingBalance(BigDecimal.ZERO); // find if outlet is in pending
            summary.setLastPayout(objectMapper.createObjectNode());
            summary.setBeneficiaryStatus(objectMapper.createObjectNode());
        } catch (Exception e) {
            summary.setPendingBalance(BigDecimal.ZERO);
        }
        
        return summary;
    }
}
