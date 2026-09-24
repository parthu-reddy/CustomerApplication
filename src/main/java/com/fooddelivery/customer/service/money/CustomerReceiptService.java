package com.fooddelivery.customer.service.money;

import com.fooddelivery.customer.dto.CustomerReceipt;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.RefundRepository;
import com.fooddelivery.order.refund.RefundView;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@lombok.RequiredArgsConstructor
public class CustomerReceiptService {
    private final IOrderRepository orderRepository;
    private final RefundRepository refundRepository;

    public CustomerReceipt getReceipt(UUID orderId, UUID customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.getCustomerId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }
        
        CustomerReceipt receipt = new CustomerReceipt();
        receipt.setDeliveryFee(requireFinancial(order.getDeliveryFee(), "deliveryFee"));
        receipt.setPlatformFee(requireFinancial(order.getCustomerPlatformFee(), "customerPlatformFee"));
        receipt.setSgst(requireFinancial(order.getSgst(), "sgst"));
        receipt.setCgst(requireFinancial(order.getCgst(), "cgst"));
        receipt.setTotal(requireFinancial(order.getTotalAmount(), "totalAmount"));
        receipt.setPaymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null);
        receipt.setPaidAt(order.getCreatedAt()); // approximate, no paidAt field
        receipt.setStoreCreditUsed(BigDecimal.ZERO); // default
        
        BigDecimal itemTotal = BigDecimal.ZERO;
        if (order.getOrderItems() != null) {
            receipt.setItems(order.getOrderItems().stream().map(i -> {
                CustomerReceipt.ReceiptItem item = new CustomerReceipt.ReceiptItem();
                // The dish name the order line stored; this printed "Item <uuid>" for every line.
                item.setName(i.getName() != null ? i.getName() : "Item");
                item.setQuantity(i.getQuantity());
                item.setPrice(i.getPrice());
                return item;
            }).collect(Collectors.toList()));
            
            itemTotal = order.getOrderItems().stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            receipt.setItems(java.util.Collections.emptyList());
        }
        receipt.setItemTotal(itemTotal);
        
        java.util.List<RefundView> refunds = refundRepository.findByOrderId(orderId).stream()
            .map(r -> RefundView.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .amount(r.getAmount())
                .status(r.getStatus())
                .destination(r.getDestination())
                .reasonCode(r.getReasonCode())
                .requestedAt(r.getCreatedAt())
                .completedAt(r.getUpdatedAt())
                .build())
            .collect(Collectors.toList());
            
        receipt.setRefunds(refunds);
        return receipt;
    }

    private BigDecimal requireFinancial(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalStateException("Missing financial value for: " + field);
        }
        return value;
    }
}
