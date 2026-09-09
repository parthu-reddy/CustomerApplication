package com.fooddelivery.customer.service.money;

import com.fooddelivery.customer.dto.RestaurantSummary;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.customer.client.LedgerClient;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;
import com.fooddelivery.customer.dto.PayoutSummaryDto;
import com.fooddelivery.customer.dto.BeneficiaryStatusDto;

@Service
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class RestaurantSummaryService {
    private final IOrderRepository orderRepository;
    private final LedgerClient ledgerClient;

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

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static PayoutSummaryDto toPayoutSummary(com.fooddelivery.common.dto.ledger.PayoutDto payout) {
        if (payout == null) return null;
        return PayoutSummaryDto.builder()
                .payoutId(payout.getId() != null ? payout.getId().toString() : null)
                .amount(payout.getAmount())
                .status(payout.getStatus())
                .timestamp(payout.getPaidAt() != null ? payout.getPaidAt().toInstant() : null)
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
