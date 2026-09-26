package com.fooddelivery.customer.service.money;

import com.fooddelivery.customer.dto.RestaurantSummary;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.customer.client.LedgerClient;
import com.fooddelivery.customer.client.RestaurantClient;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.common.time.IanaTimeZoneValidator;
import com.fooddelivery.common.time.TimeWindow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import com.fooddelivery.customer.dto.PayoutSummaryDto;
import com.fooddelivery.customer.dto.BeneficiaryStatusDto;

@Service
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class RestaurantSummaryService {
    private final IOrderRepository orderRepository;
    private final LedgerClient ledgerClient;
    private final RestaurantClient restaurantClient;
    private final Clock clock;

    public RestaurantSummary getSummary(UUID outletId, SummaryPeriod period) {
        RestaurantSummary summary = new RestaurantSummary();

        // The outlet's day, week or month, not the server's: at 20:00Z an outlet in Kolkata is
        // already on tomorrow. This was a fixed "one UTC month back" whatever period was asked for.
        TimeWindow window = period.window(clock.instant(), outletZone(outletId));
        // Delivered orders only, dated when delivered: the ledger pays nothing on the rest.
        List<Order> orders = orderRepository.findByRestaurantIdDeliveredInWindow(outletId, window.from(), window.to());
        summary.setOrders(orders.size());
        
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal platformFees = BigDecimal.ZERO;
        BigDecimal deliveryContrib = BigDecimal.ZERO;
        BigDecimal platformBonus = BigDecimal.ZERO;
        BigDecimal netEarnings = BigDecimal.ZERO;
        
        for (Order o : orders) {
            if (o.getOrderItems() != null) {
                gross = gross.add(o.getOrderItems().stream()
                    .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            if (o.getRestaurantPlatformFee() != null) platformFees = platformFees.add(o.getRestaurantPlatformFee());
            if (o.getRestaurantDeliveryContribution() != null) deliveryContrib = deliveryContrib.add(o.getRestaurantDeliveryContribution());
            if (o.getPlatformBonus() != null) platformBonus = platformBonus.add(o.getPlatformBonus());
            if (o.getRestaurantPayout() != null) netEarnings = netEarnings.add(o.getRestaurantPayout());
        }
        
        summary.setGrossFoodCost(gross);
        summary.setPlatformFees(platformFees);
        summary.setDeliveryContribution(deliveryContrib);
        summary.setNetEarnings(netEarnings);
        // The restaurant pays it per order (PLATFORM_BONUS), and restaurantPayout is already net of it.
        // Both it and the clawbacks were hardcoded to zero, so the Clawbacks card always read zero.
        summary.setPlatformBonus(platformBonus);
        summary.setClawbacks(clawbacks(outletId, window));
        
        // These three used to be hardcoded: the ledger was called for the admin-only queue of every
        // payee on the platform, the response was thrown away, and the card always read zero with an
        // empty last payout. They now come from this outlet's own summary.
        try {
            var payeeSummary = ledgerClient.getPayeeSummary("RESTAURANT", outletId);
            summary.setPendingBalance(nz(payeeSummary.getUnsettledAmount()));
            summary.setLastPayout(toPayoutSummary(payeeSummary.getLastPayout()));
            summary.setBeneficiaryStatus(toBeneficiaryStatus(payeeSummary.getBeneficiary()));
        } catch (Exception e) {
            // Degrade rather than 500 the whole earnings screen, but do not invent a balance.
            log.warn("Ledger summary unavailable for outlet {}: {}", outletId, e.getMessage());
            summary.setPendingBalance(null);
            summary.setLastPayout(null);
            summary.setBeneficiaryStatus(null);
        }
        
        return summary;
    }

    /**
     * What was clawed back from the outlet in {@code window}, for refunds that were its fault. Read from
     * the ledger, because each clawback is capped by the ones booked before it (LedgerBookkeeper.bookRefund):
     * recomputing them here could disagree with what the outlet was actually charged. Dated when booked,
     * as its statement shows them. Null, never zero, when the ledger cannot say: zero reads as "none".
     */
    private BigDecimal clawbacks(UUID outletId, TimeWindow window) {
        try {
            return nz(ledgerClient.getCategoryTotal(LedgerAccountType.RESTAURANT_PAYABLE, outletId,
                    ChargeCategory.CLAWBACK, TransactionDirection.DEBIT, window.from(), window.to()));
        } catch (Exception e) {
            log.warn("Ledger clawbacks unavailable for outlet {}: {}", outletId, e.getMessage());
            return null;
        }
    }

    /**
     * The outlet's {@code time_zone}. Refuses with 503 rather than guess one, as
     * {@link CustomerInvoiceService#issuedOn} does: totals summed on the wrong calendar put orders in
     * the wrong day and look just as authoritative as the right ones.
     */
    private ZoneId outletZone(UUID outletId) {
        Map<String, String> outlet;
        try {
            outlet = restaurantClient.getOutletSummary(outletId);
        } catch (Exception e) {
            log.warn("Outlet zone unavailable for outlet {}: {}", outletId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not load earnings right now. Try again shortly.");
        }
        String id = outlet == null ? null : outlet.get("timeZone");
        if (!IanaTimeZoneValidator.isRegionId(id)) {
            log.warn("Outlet {} has no usable time zone: {}", outletId, id);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not load earnings right now. Try again shortly.");
        }
        return ZoneId.of(id);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static PayoutSummaryDto toPayoutSummary(com.fooddelivery.common.dto.ledger.PayoutDto payout) {
        if (payout == null) return null;
        return PayoutSummaryDto.builder()
                .payoutId(payout.getId() != null ? payout.getId().toString() : null)
                .amount(payout.getAmount())
                .status(payout.getStatus())
                .timestamp(payout.getPaidAt())
                .build();
    }

    static BeneficiaryStatusDto toBeneficiaryStatus(com.fooddelivery.common.dto.ledger.BeneficiaryResponse b) {
        if (b == null) return null;
        return BeneficiaryStatusDto.builder()
                .beneficiaryId(b.getAccountNumberMasked())
                .verificationStatus(b.isVerified() ? "VERIFIED" : "UNVERIFIED")
                .active(b.isVerified())
                .build();
    }
}
