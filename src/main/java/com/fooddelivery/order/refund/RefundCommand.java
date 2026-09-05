package com.fooddelivery.order.refund;

import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.order.enums.RefundSource;
import com.fooddelivery.order.enums.InitiatorType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class RefundCommand {
    private UUID orderId;
    private BigDecimal amount;
    private List<Item> items;
    private String reasonCode;
    private String reasonText;
    private FaultType faultType;
    private RefundDestination destination; 
    private RefundSource source;
    private InitiatorType initiatorType;
    private UUID initiatorId;
    private String idempotencyKey;
    private UUID ticketId;

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class Item {
        private UUID orderItemId;
        private int quantity;
    }
}
