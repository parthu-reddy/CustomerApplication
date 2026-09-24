package com.fooddelivery.customer.service.money;

import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.customer.client.RestaurantClient;
import com.fooddelivery.customer.config.InvoiceOperatorConfig;
import com.fooddelivery.customer.dto.CustomerInvoice;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderInvoice;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.OrderInvoiceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and reads GST tax invoices (Phase 7 A6).
 *
 * <p>An invoice exists only for a delivered order, and is issued once: the first request numbers
 * it and snapshots the supplier; every later request reads the same row. It replaces the fake
 * "Download PDF Invoice" the delivered screen used to show.
 */
@Service
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class CustomerInvoiceService {

    static final String SAC_RESTAURANT_SERVICE = "996331";

    private final IOrderRepository orderRepository;
    private final OrderInvoiceRepository invoiceRepository;
    private final InvoiceNumberAllocator numberAllocator;
    private final RestaurantClient restaurantClient;
    private final InvoiceOperatorConfig operatorConfig;

    public CustomerInvoice getInvoice(UUID orderId, UUID customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.getCustomerId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }
        if (order.getDeliveredAt() == null && order.getDeliveryStatus() != DeliveryStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An invoice is issued once the order is delivered");
        }
        OrderInvoice invoice = invoiceRepository.findById(orderId).orElseGet(() -> issue(order));
        return toDto(order, invoice);
    }

    private OrderInvoice issue(Order order) {
        Map<String, Object> supplier;
        try {
            supplier = restaurantClient.getInvoiceDetails(order.getRestaurantId());
        } catch (Exception e) {
            // Never issue an invoice without its supplier: the number would be spent on a
            // document that cannot be corrected once issued.
            log.warn("Supplier details unavailable for order {}: {}", order.getId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not prepare the invoice right now. Try again shortly.");
        }
        LocalDateTime issuedAt = order.getDeliveredAt() != null ? order.getDeliveredAt() : LocalDateTime.now();
        OrderInvoice invoice = OrderInvoice.builder()
                .orderId(order.getId())
                .invoiceNumber(numberAllocator.next(issuedAt.toLocalDate()))
                .issuedAt(issuedAt)
                .supplierLegalName(text(supplier.get("legalEntityName")))
                .supplierTradeName(text(supplier.get("outletName")))
                .supplierGstin(text(supplier.get("gstin")))
                .supplierFssai(text(supplier.get("fssaiLicenseNumber")))
                .build();
        try {
            return invoiceRepository.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException raced) {
            // Two first requests at once: the other one issued it. Read theirs.
            return invoiceRepository.findById(order.getId()).orElseThrow(() -> raced);
        }
    }

    private CustomerInvoice toDto(Order order, OrderInvoice invoice) {
        List<CustomerInvoice.Line> lines = order.getOrderItems() == null ? List.of() : order.getOrderItems().stream()
                .sorted(Comparator.comparing(i -> i.getName() == null ? "" : i.getName()))
                .map(i -> CustomerInvoice.Line.builder()
                        .description(i.getName() != null ? i.getName() : "Item")
                        .sac(SAC_RESTAURANT_SERVICE)
                        .quantity(i.getQuantity())
                        .unitPrice(i.getPrice())
                        .amount(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .build())
                .toList();
        BigDecimal taxable = lines.stream().map(CustomerInvoice.Line::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return CustomerInvoice.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .issuedAt(invoice.getIssuedAt())
                .orderId(order.getId())
                .orderPlacedAt(order.getCreatedAt())
                .supplier(CustomerInvoice.Party.builder()
                        .legalName(invoice.getSupplierLegalName())
                        .tradeName(invoice.getSupplierTradeName() != null ? invoice.getSupplierTradeName() : order.getRestaurantName())
                        .gstin(invoice.getSupplierGstin())
                        .fssaiLicenseNumber(invoice.getSupplierFssai())
                        .build())
                .operator(operatorConfig.getLegalName() == null || operatorConfig.getLegalName().isBlank() ? null
                        : CustomerInvoice.Party.builder()
                        .legalName(operatorConfig.getLegalName())
                        .gstin(operatorConfig.getGstin())
                        .address(operatorConfig.getAddress())
                        .build())
                .customerName(order.getCustomerName())
                .deliveryAddress(order.getDeliveryAddress())
                .lines(lines)
                .taxableValue(taxable)
                .cgstAmount(order.getCgst())
                .cgstRatePercent(ratePercent(order.getCgst(), taxable))
                .sgstAmount(order.getSgst())
                .sgstRatePercent(ratePercent(order.getSgst(), taxable))
                .deliveryFee(order.getDeliveryFee())
                .platformFee(order.getCustomerPlatformFee())
                .total(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null)
                .build();
    }

    /** The rate actually charged, e.g. 2.5, from the amounts -- rounded to 0.1 because amounts are rounded to the paisa. */
    static BigDecimal ratePercent(BigDecimal tax, BigDecimal base) {
        if (tax == null || base == null || base.signum() == 0) return null;
        return tax.multiply(BigDecimal.valueOf(100)).divide(base, 1, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
