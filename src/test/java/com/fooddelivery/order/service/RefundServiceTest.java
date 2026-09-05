package com.fooddelivery.order.service;

import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.Refund;
import com.fooddelivery.order.repository.RefundRepository;
import com.fooddelivery.order.refund.RefundService;
import com.fooddelivery.order.ledger.LedgerBookkeeper;
import com.fooddelivery.common.enums.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.IPaymentIntentRepository;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.client.WalletInternalClient;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private RefundRepository refundRepository;
    @Mock private IOrderRepository orderRepository;
    @Mock private IPaymentIntentRepository paymentIntentRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private LedgerBookkeeper ledgerBookkeeper;
    @Mock private ObjectMapper objectMapper;
    @Mock private WalletInternalClient walletClient;
    
    @InjectMocks
    private RefundService refundService;

    @Test
    void complete_shouldProcessRefundSuccessfully() {
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund();
        refund.setId(refundId);
        refund.setStatus(RefundStatus.PROCESSING);
        
        Order order = new Order();
        order.setId(UUID.randomUUID());
        refund.setOrderId(order.getId());
        
        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        
        refundService.complete(refundId, "PAY-REF-123");
        
        verify(ledgerBookkeeper).bookRefund(eq(order), eq(refund));
    }
}
