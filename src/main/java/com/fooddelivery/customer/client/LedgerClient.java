package com.fooddelivery.customer.client;

import com.fooddelivery.common.dto.PageResponseDto;
import com.fooddelivery.common.dto.ledger.CashRemittanceDto;
import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;
import com.fooddelivery.common.dto.ledger.PayoutDto;
import com.fooddelivery.common.dto.ledger.PendingPayoutResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "ledger-service", contextId = "ledgerClient", fallback = LedgerClientFallback.class)
public interface LedgerClient {

    @GetMapping("/api/v1/ledger/statements/{ownerType}/{ownerId}")
    PageResponseDto<LedgerStatementLineDto> getStatement(
            @PathVariable("ownerType") String ownerType, 
            @PathVariable("ownerId") UUID ownerId, 
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/api/v1/admin/payouts/pending")
    List<PendingPayoutResponseDto> getPendingPayouts(
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/api/v1/admin/payouts")
    PageResponseDto<PayoutDto> getPayouts(
            @RequestParam("payeeType") String payeeType,
            @RequestParam("payeeId") UUID payeeId,
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/api/v1/admin/cash/drivers/{driverId}")
    PageResponseDto<CashRemittanceDto> getCashByDriver(
            @PathVariable("driverId") UUID driverId,
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/api/v1/ledger/statements/references/{referenceId}")
    List<LedgerStatementLineDto> getStatementByReference(
            @PathVariable("referenceId") UUID referenceId);
}
