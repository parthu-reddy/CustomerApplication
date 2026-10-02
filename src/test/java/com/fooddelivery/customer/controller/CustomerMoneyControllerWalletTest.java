package com.fooddelivery.customer.controller;

import com.fooddelivery.common.client.WalletServiceClient;
import com.fooddelivery.common.dto.PageResponseDto;
import com.fooddelivery.common.dto.wallet.CreateWalletRequest;
import com.fooddelivery.common.dto.wallet.WalletDto;
import com.fooddelivery.common.enums.WalletEntityType;
import com.fooddelivery.customer.service.money.CustomerInvoiceService;
import com.fooddelivery.customer.service.money.CustomerReceiptService;
import com.fooddelivery.money.controller.CustomerMoneyController;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerMoneyControllerWalletTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private WalletServiceClient walletServiceClient;
    private CustomerMoneyController controller;
    private Principal principal;
    private RefundRepository refunds;
    private IOrderRepository orders;

    @BeforeEach
    void setUp() {
        walletServiceClient = mock(WalletServiceClient.class);
        refunds = mock(RefundRepository.class);
        orders = mock(IOrderRepository.class);
        controller = new CustomerMoneyController(
                refunds,
                orders,
                mock(CustomerReceiptService.class),
                mock(CustomerInvoiceService.class),
                walletServiceClient);
        principal = () -> CUSTOMER_ID.toString();
    }

    @Test
    void walletReadCreatesTheCustomersZeroBalanceWalletWhenItDoesNotExistYet() {
        WalletDto wallet = new WalletDto();
        wallet.setEntityId(CUSTOMER_ID);
        wallet.setEntityType(WalletEntityType.CUSTOMER);
        when(walletServiceClient.getOrCreateWallet(org.mockito.ArgumentMatchers.any())).thenReturn(wallet);

        assertThat(controller.getMyWallet(principal).getBody()).isSameAs(wallet);

        ArgumentCaptor<CreateWalletRequest> request = ArgumentCaptor.forClass(CreateWalletRequest.class);
        verify(walletServiceClient).getOrCreateWallet(request.capture());
        assertThat(request.getValue().getEntityId()).isEqualTo(CUSTOMER_ID);
        assertThat(request.getValue().getEntityType()).isEqualTo(WalletEntityType.CUSTOMER);
        assertThat(request.getValue().getCurrency()).isEqualTo("INR");
    }

    @Test
    void transactionReadEstablishesTheWalletBeforeReadingItsHistory() {
        @SuppressWarnings("unchecked")
        PageResponseDto<Map<String, Object>> transactions = mock(PageResponseDto.class);
        when(walletServiceClient.getOrCreateWallet(org.mockito.ArgumentMatchers.any())).thenReturn(new WalletDto());
        when(walletServiceClient.getWalletTransactions("CUSTOMER", CUSTOMER_ID, 2, 20)).thenReturn(transactions);

        assertThat(controller.getMyWalletTransactions(principal, 2, 20).getBody()).isSameAs(transactions);

        verify(walletServiceClient).getOrCreateWallet(org.mockito.ArgumentMatchers.any());
        verify(walletServiceClient).getWalletTransactions("CUSTOMER", CUSTOMER_ID, 2, 20);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = com.fooddelivery.common.enums.RefundStatus.class, names = {"PROCESSING", "COMPLETED"})
    void orderRefundReadReportsTheActualCompletionTime(com.fooddelivery.common.enums.RefundStatus status) {
        UUID orderId = UUID.randomUUID();
        var order = new com.fooddelivery.order.entity.Order();
        order.setCustomerId(CUSTOMER_ID);
        var refund = new com.fooddelivery.order.entity.Refund();
        refund.setStatus(status);
        refund.setAmount(new java.math.BigDecimal("120.50"));
        refund.setUpdatedAt(java.time.Instant.parse("2026-10-02T08:00:00Z"));
        var completedAt = status == com.fooddelivery.common.enums.RefundStatus.COMPLETED
                ? java.time.Instant.parse("2026-10-02T07:55:00Z") : null;
        refund.setCompletedAt(completedAt);
        when(orders.findById(orderId)).thenReturn(java.util.Optional.of(order));
        when(refunds.findByOrderId(orderId)).thenReturn(java.util.List.of(refund));
        var result = controller.getOrderRefunds(orderId, principal).getBody();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("120.50");
        assertThat(result.get(0).getCompletedAt()).isEqualTo(completedAt);
    }

}
