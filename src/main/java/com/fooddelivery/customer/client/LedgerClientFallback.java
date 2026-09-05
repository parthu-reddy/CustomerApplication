package com.fooddelivery.customer.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LedgerClientFallback implements LedgerClient {

    @Override
    public JsonNode getStatement(String ownerType, UUID ownerId, int page, int size) {
        throw new IllegalStateException("Ledger service is unavailable");
    }

    @Override
    public JsonNode getPendingPayouts(int page, int size) {
        throw new IllegalStateException("Ledger service is unavailable");
    }

    @Override
    public JsonNode getPayouts(String payeeType, UUID payeeId, int page, int size) {
        throw new IllegalStateException("Ledger service is unavailable");
    }

    @Override
    public JsonNode getCashByDriver(UUID driverId, int page, int size) {
        throw new IllegalStateException("Ledger service is unavailable");
    }

    @Override
    public java.util.List<com.fooddelivery.common.dto.ledger.LedgerStatementLineDto> getStatementByReference(UUID referenceId) {
        throw new IllegalStateException("Ledger service is unavailable");
    }
}
