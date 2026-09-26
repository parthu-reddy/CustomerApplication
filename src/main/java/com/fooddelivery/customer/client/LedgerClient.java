package com.fooddelivery.customer.client;

import com.fooddelivery.common.dto.PageResponseDto;
import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;
import com.fooddelivery.common.dto.ledger.PayeeMoneySummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * CustomerApplication calls the ledger with a SERVICE identity, so every path here must be one the
 * SERVICE role can reach.
 *
 * <p>This client used to call {@code /api/v1/internal/admin/payouts/**} and
 * {@code /api/v1/internal/admin/cash/**}, all of which are {@code hasRole('ADMIN')}: every call was
 * a 403 that the callers swallowed, which is why the restaurant and rider money cards showed zero.
 * Moving the controllers under {@code /api/v1/internal/admin} and repointing the client at the same
 * admin paths did not change that -- the guard is the role, not the prefix.
 */
@FeignClient(name = "ledger-service", contextId = "ledgerClient", fallback = LedgerClientFallback.class)
public interface LedgerClient {

    @GetMapping("/api/v1/ledger/statements/{ownerType}/{ownerId}")
    PageResponseDto<LedgerStatementLineDto> getStatement(
            @PathVariable("ownerType") String ownerType, 
            @PathVariable("ownerId") UUID ownerId, 
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/api/v1/internal/ledger/payouts/latest/{payeeType}/{payeeId}")
    PayeeMoneySummaryDto getPayeeSummary(
            @PathVariable("payeeType") String payeeType,
            @PathVariable("payeeId") UUID payeeId);

    @GetMapping("/api/v1/internal/ledger/payouts")
    PageResponseDto<com.fooddelivery.common.dto.ledger.PayoutDto> getPayouts(
            @RequestParam("payeeType") String payeeType,
            @RequestParam("payeeId") UUID payeeId,
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size);

    /**
     * What one owner's account moved under one category in {@code [from, to)}. The restaurant earnings
     * summary reads an outlet's clawbacks from here: only the ledger knows what was actually clawed
     * back, because each clawback is capped by the ones booked before it.
     */
    @GetMapping("/api/v1/internal/ledger/accounts/{ownerType}/{ownerId}/totals")
    java.math.BigDecimal getCategoryTotal(
            @PathVariable("ownerType") com.fooddelivery.common.enums.LedgerAccountType ownerType,
            @PathVariable("ownerId") UUID ownerId,
            @RequestParam("category") com.fooddelivery.common.enums.ChargeCategory category,
            @RequestParam("direction") com.fooddelivery.common.enums.TransactionDirection direction,
            @RequestParam("from") java.time.Instant from,
            @RequestParam("to") java.time.Instant to);

    @GetMapping("/api/v1/internal/ledger/statements/references/{referenceId}")
    List<LedgerStatementLineDto> getStatementByReference(
            @PathVariable("referenceId") UUID referenceId);
}
