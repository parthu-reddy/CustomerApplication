package com.fooddelivery.customer.service.money;

import com.fooddelivery.customer.dto.DriverSummary;
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
public class DriverSummaryService {
    private final IOrderRepository orderRepository;
    private final LedgerClient ledgerClient;
    private final ObjectMapper objectMapper;

    public DriverSummary getSummary(UUID driverId, String period) {
        DriverSummary summary = new DriverSummary();
        
        java.time.LocalDateTime start = java.time.LocalDateTime.now().minusMonths(1);
        java.time.LocalDateTime end = java.time.LocalDateTime.now().plusDays(1);
        
        List<com.fooddelivery.common.enums.OrderStatus> cancelledStatuses = List.of(com.fooddelivery.common.enums.OrderStatus.CANCELLED);
        List<com.fooddelivery.common.enums.DeliveryStatus> terminalStatuses = List.of(com.fooddelivery.common.enums.DeliveryStatus.DELIVERED);
        org.springframework.data.domain.Page<Order> ordersPage = orderRepository.findHistoryOrdersForDriver(
                driverId, cancelledStatuses, terminalStatuses, start, end, org.springframework.data.domain.PageRequest.of(0, 1000));
        
        List<Order> orders = ordersPage.getContent();
        summary.setDeliveries(orders.size());
        
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal taxes = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        
        for (Order o : orders) {
            if (o.getDriverGrossPayout() != null) gross = gross.add(o.getDriverGrossPayout());
            if (o.getDriverTaxes() != null) taxes = taxes.add(o.getDriverTaxes());
            if (o.getDriverNetPayout() != null) net = net.add(o.getDriverNetPayout());
        }
        
        summary.setGross(gross);
        summary.setTaxes(taxes);
        summary.setNet(net);
        summary.setCashCollected(BigDecimal.ZERO);
        summary.setCashRemitted(BigDecimal.ZERO);
        summary.setCashInHand(BigDecimal.ZERO);
        
        try {
            JsonNode pending = ledgerClient.getPendingPayouts(0, 100);
            summary.setPendingBalance(BigDecimal.ZERO);
            summary.setLastPayout(objectMapper.createObjectNode());
        } catch (Exception e) {
            summary.setPendingBalance(BigDecimal.ZERO);
        }
        
        return summary;
    }
}
