package com.fooddelivery.customer.service.money;

import com.fooddelivery.customer.dto.DriverSummary;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.common.time.TimeWindow;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;
import com.fooddelivery.customer.dto.PayoutSummaryDto;

@Service
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class DriverSummaryService {
    private final IOrderRepository orderRepository;
    private final LedgerClient ledgerClient;

    /**
     * The rider's earnings for {@code window}: a period on the rider's own calendar, which the rider's
     * browser computes, because a rider has no zone on record. This used to ignore the period it was
     * asked for and sum one UTC month back from now, capped at 1000 orders.
     * TimezoneCorrectness_2026-09-25.
     *
     * <p>Delivered orders only, dated when delivered, which is what the ledger pays and when. The
     * payout amounts are quoted onto every order when it is placed, so summing cancelled orders (as
     * this did) reported money the rider was never going to receive.
     */
    public DriverSummary getSummary(UUID driverId, TimeWindow window) {
        DriverSummary summary = new DriverSummary();

        List<Order> orders = orderRepository.findByDeliveryExecutiveIdDeliveredInWindow(driverId, window.from(), window.to());
        summary.setDeliveries(orders.size());
        
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal taxes = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal tips = BigDecimal.ZERO;
        
        for (Order o : orders) {
            if (o.getDriverGrossPayout() != null) gross = gross.add(o.getDriverGrossPayout());
            if (o.getDriverTaxes() != null) taxes = taxes.add(o.getDriverTaxes());
            if (o.getDriverNetPayout() != null) net = net.add(o.getDriverNetPayout());
            // The tip is paid to the rider in full (RiderTip), untaxed: it adds to gross and net alike.
            if (o.getTipAmount() != null) tips = tips.add(o.getTipAmount());
        }
        gross = gross.add(tips);
        net = net.add(tips);
        summary.setTips(tips);
        
        summary.setGross(gross);
        summary.setTaxes(taxes);
        summary.setNet(net);
        try {
            var payeeSummary = ledgerClient.getPayeeSummary("DRIVER", driverId);
            summary.setPendingBalance(payeeSummary.getUnsettledAmount() == null
                    ? BigDecimal.ZERO : payeeSummary.getUnsettledAmount());
            summary.setLastPayout(RestaurantSummaryService.toPayoutSummary(payeeSummary.getLastPayout()));
        } catch (Exception e) {
            log.warn("Ledger summary unavailable for driver {}: {}", driverId, e.getMessage());
            summary.setPendingBalance(null);
            summary.setLastPayout(null);
        }
        
        return summary;
    }
}
