package com.fooddelivery.customer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

@FeignClient(name = "ledger-service", contextId = "ledgerClient", fallback = LedgerClientFallback.class)
public interface LedgerClient {

    @GetMapping("/api/v1/ledger/statements/{ownerType}/{ownerId}")
    JsonNode getStatement(
            @PathVariable("ownerType") String ownerType, 
            @PathVariable("ownerId") UUID ownerId, 
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/api/v1/admin/payouts/pending")
    JsonNode getPendingPayouts(
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/api/v1/admin/payouts")
    JsonNode getPayouts(
            @RequestParam("payeeType") String payeeType,
            @RequestParam("payeeId") UUID payeeId,
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/api/v1/admin/cash/drivers/{driverId}")
    JsonNode getCashByDriver(
            @PathVariable("driverId") UUID driverId,
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/api/v1/ledger/statements/references/{referenceId}")
    java.util.List<com.fooddelivery.common.dto.ledger.LedgerStatementLineDto> getStatementByReference(
            @PathVariable("referenceId") UUID referenceId);
}
