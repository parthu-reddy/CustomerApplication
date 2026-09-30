package com.fooddelivery.customer.controller;

import com.fooddelivery.order.enums.FaultType;
import com.fooddelivery.order.enums.InitiatorType;
import com.fooddelivery.order.enums.RefundSource;
import com.fooddelivery.order.refund.RefundCommand;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.refund.RefundView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRefundCommandControllerTest {

    @Mock
    private RefundService refundService;

    @InjectMocks
    private AdminRefundCommandController controller;

    @Test
    void directRefundDerivesEverySensitiveFieldAndUsesTheServerQuote() {
        UUID adminId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        AdminRefundCommandController.AdminRefundRequest request =
                new AdminRefundCommandController.AdminRefundRequest(
                        orderId,
                        List.of(new AdminRefundCommandController.RefundItemRequest(itemId, 2)),
                        FaultType.RESTAURANT_FAULT,
                        "  missing item  ");
        RefundView expected = RefundView.builder().id(UUID.randomUUID()).orderId(orderId).build();

        when(refundService.quote(eq(orderId), anyList())).thenReturn(new BigDecimal("24.50"));
        when(refundService.request(any())).thenReturn(expected);

        assertEquals(expected, controller.requestRefund(request, admin(adminId)).getBody());

        ArgumentCaptor<RefundCommand> command = ArgumentCaptor.forClass(RefundCommand.class);
        verify(refundService).request(command.capture());
        RefundCommand captured = command.getValue();
        assertEquals(orderId, captured.getOrderId());
        assertEquals(new BigDecimal("24.50"), captured.getAmount());
        assertEquals(RefundSource.ADMIN, captured.getSource());
        assertEquals(InitiatorType.ADMIN, captured.getInitiatorType());
        assertEquals(adminId, captured.getInitiatorId());
        assertEquals("ADMIN_DIRECT", captured.getReasonCode());
        assertEquals("missing item", captured.getReasonText());
        assertEquals(FaultType.RESTAURANT_FAULT, captured.getFaultType());
        assertEquals(null, captured.getDestination());
        assertEquals(null, captured.getTicketId());
        assertEquals(1, captured.getItems().size());
        assertEquals(itemId, captured.getItems().get(0).getOrderItemId());
        assertEquals(2, captured.getItems().get(0).getQuantity());
        assertEquals(true, captured.getIdempotencyKey().startsWith("admin_direct_refund:" + adminId + ":"));
    }

    @Test
    void anEquivalentRetryGetsTheSameServerDerivedIdempotencyKeyRegardlessOfItemOrder() {
        UUID adminId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID firstItem = UUID.randomUUID();
        UUID secondItem = UUID.randomUUID();
        AdminRefundCommandController.AdminRefundRequest first =
                new AdminRefundCommandController.AdminRefundRequest(orderId,
                        List.of(new AdminRefundCommandController.RefundItemRequest(firstItem, 1),
                                new AdminRefundCommandController.RefundItemRequest(secondItem, 2)),
                        FaultType.PLATFORM_FAULT, "late delivery");
        AdminRefundCommandController.AdminRefundRequest reordered =
                new AdminRefundCommandController.AdminRefundRequest(orderId,
                        List.of(new AdminRefundCommandController.RefundItemRequest(secondItem, 2),
                                new AdminRefundCommandController.RefundItemRequest(firstItem, 1)),
                        FaultType.PLATFORM_FAULT, "late delivery");

        when(refundService.quote(eq(orderId), anyList())).thenReturn(new BigDecimal("15.00"));
        when(refundService.request(any())).thenReturn(RefundView.builder().build());

        controller.requestRefund(first, admin(adminId));
        controller.requestRefund(reordered, admin(adminId));

        ArgumentCaptor<RefundCommand> commands = ArgumentCaptor.forClass(RefundCommand.class);
        verify(refundService, org.mockito.Mockito.times(2)).request(commands.capture());
        assertEquals(commands.getAllValues().get(0).getIdempotencyKey(),
                commands.getAllValues().get(1).getIdempotencyKey());
    }

    @Test
    void unauthenticatedOrMalformedPrincipalCannotChooseAnActor() {
        UUID orderId = UUID.randomUUID();
        AdminRefundCommandController.AdminRefundRequest request =
                new AdminRefundCommandController.AdminRefundRequest(orderId,
                        List.of(new AdminRefundCommandController.RefundItemRequest(UUID.randomUUID(), 1)),
                        FaultType.UNKNOWN, "reason");

        assertThrows(AccessDeniedException.class, () -> controller.requestRefund(request, null));
        assertThrows(AccessDeniedException.class, () -> controller.requestRefund(request,
                new UsernamePasswordAuthenticationToken("not-a-uuid", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))));
        verify(refundService, never()).quote(any(), anyList());
        verify(refundService, never()).request(any());
    }

    @Test
    void publicRequestCannotCarryInternalCommandFields() {
        List<String> fields = Arrays.stream(AdminRefundCommandController.AdminRefundRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();
        for (String internalField : List.of("amount", "source", "destination", "initiatorType", "initiatorId",
                "idempotencyKey", "ticketId", "reasonCode")) {
            assertFalse(fields.contains(internalField), internalField + " must be server-owned");
        }
    }

    private Authentication admin(UUID id) {
        return new UsernamePasswordAuthenticationToken(id.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
