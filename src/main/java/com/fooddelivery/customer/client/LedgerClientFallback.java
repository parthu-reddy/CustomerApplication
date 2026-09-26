package com.fooddelivery.customer.client;

import com.fooddelivery.common.dto.PageResponseDto;
import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;
import com.fooddelivery.common.dto.ledger.PayeeMoneySummaryDto;
import com.fooddelivery.common.dto.ledger.PayoutDto;
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
    public PayeeMoneySummaryDto getPayeeSummary(String payeeType, UUID payeeId) {
        throw new IllegalStateException("Ledger service is unavailable");
    }

    @Override
    public PageResponseDto<PayoutDto> getPayouts(String payeeType, UUID payeeId, int page, int size) {
        throw new IllegalStateException("Ledger service is unavailable");
    }

    @Override
    public java.math.BigDecimal getCategoryTotal(com.fooddelivery.common.enums.LedgerAccountType ownerType, UUID ownerId,
                                                 com.fooddelivery.common.enums.ChargeCategory category,
                                                 com.fooddelivery.common.enums.TransactionDirection direction,
                                                 java.time.Instant from, java.time.Instant to) {
        // Never a zero: it would read as "nothing clawed back".
        throw new IllegalStateException("Ledger service is unavailable");
    }

    @Override
    public List<LedgerStatementLineDto> getStatementByReference(UUID referenceId) {
        throw new IllegalStateException("Ledger service is unavailable");
    }
}
