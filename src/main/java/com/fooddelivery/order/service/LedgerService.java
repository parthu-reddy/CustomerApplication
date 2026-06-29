package com.fooddelivery.order.service;

import com.fooddelivery.order.entity.Ledger;
import com.fooddelivery.order.repository.ILedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LedgerService {

    private final ILedgerRepository ledgerRepository;

    @Transactional
    public void credit(UUID accountId, BigDecimal amount, String transactionRef) {
        log.info("Crediting {} to account {} (Ref: {})", amount, accountId, transactionRef);
        
        Ledger latest = ledgerRepository.findFirstByAccountIdOrderByCreatedAtDesc(accountId).orElse(null);
        BigDecimal currentBalance = latest != null ? latest.getBalanceAfter() : BigDecimal.ZERO;
        
        Ledger newEntry = Ledger.builder()
                .accountId(accountId)
                .transactionRef(transactionRef)
                .type("CREDIT")
                .amount(amount)
                .balanceAfter(currentBalance.add(amount))
                .createdAt(LocalDateTime.now())
                .build();
                
        ledgerRepository.save(newEntry);
    }
}
