package com.fooddelivery.order.refund;

import com.fooddelivery.common.enums.RefundDestination;
import com.fooddelivery.common.enums.RefundStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CustomerRefundViewTest {

    @Test
    public void testView() {
        RefundView view = RefundView.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .status(RefundStatus.COMPLETED)
                .destination(RefundDestination.STORE_CREDIT)
                .requestedAt(LocalDateTime.now())
                .expectedBy(LocalDateTime.now().plusDays(2))
                .build();

        assertNotNull(view.getId());
        assertNotNull(view.getOrderId());
        assertEquals(new BigDecimal("50.00"), view.getAmount());
        assertEquals(RefundStatus.COMPLETED, view.getStatus());
        assertEquals(RefundDestination.STORE_CREDIT, view.getDestination());
        assertNotNull(view.getExpectedBy());
    }
}
