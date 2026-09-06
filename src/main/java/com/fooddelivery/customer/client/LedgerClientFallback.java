package com.fooddelivery.customer.client;

import com.fooddelivery.common.dto.PageResponseDto;
import com.fooddelivery.common.dto.ledger.CashRemittanceDto;
import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;
import com.fooddelivery.common.dto.ledger.PayoutDto;
import com.fooddelivery.common.dto.ledger.PendingPayoutResponseDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class LedgerClientFallback implements LedgerClient {

    @Override
    public PageResponseDto<LedgerStatementLineDto> getStatement(String ownerType, UUID ownerId, int page, int size) {
        throw new IllegalStateException("Ledger service is unavailable");
    }

    @Override
    public List<PendingPayoutResponseDto> getPendingPayouts(int page, int size) {
        throw new IllegalStateException("Ledger service is unavailable");
    }

    @Override
    public PageResponseDto<PayoutDto> getPayouts(String payeeType, UUID payeeId, int page, int size) {
        throw new IllegalStateException("Ledger service is unavailable");
    }

    @Override
    public PageResponseDto<CashRemittanceDto> getCashByDriver(UUID driverId, int page, int size) {
        throw new IllegalStateException("Ledger service is unavailable");
    }

    @Override
    public List<LedgerStatementLineDto> getStatementByReference(UUID referenceId) {
        throw new IllegalStateException("Ledger service is unavailable");
    }
}
