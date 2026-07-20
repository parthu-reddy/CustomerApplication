package com.fooddelivery.order.service;

import com.fooddelivery.order.entity.LedgerAccount;
import com.fooddelivery.order.entity.LedgerEntry;
import com.fooddelivery.order.repository.ILedgerAccountRepository;
import com.fooddelivery.order.repository.ILedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import com.fooddelivery.common.enums.AccountType;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoubleEntryLedgerService {

    private final ILedgerAccountRepository accountRepository;
    private final ILedgerEntryRepository entryRepository;

    @Transactional
    public void recordTransaction(UUID transactionId, UUID sourceOwnerId, AccountType sourceOwnerType, 
                                  UUID targetOwnerId, AccountType targetOwnerType, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }

        if (entryRepository.existsByTransactionId(transactionId)) {
            log.info("Transaction {} already recorded. Skipping.", transactionId);
            return;
        }

        LedgerAccount sourceAccount = getOrCreateAccount(sourceOwnerId, sourceOwnerType);
        LedgerAccount targetAccount = getOrCreateAccount(targetOwnerId, targetOwnerType);

        // Debit Source
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        accountRepository.save(sourceAccount);
        
        LedgerEntry debitEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .accountId(sourceAccount.getId())
                .direction(com.fooddelivery.common.enums.TransactionDirection.DEBIT)
                .amount(amount)
                .createdAt(LocalDateTime.now())
                .build();
        entryRepository.save(debitEntry);

        // Credit Target
        targetAccount.setBalance(targetAccount.getBalance().add(amount));
        accountRepository.save(targetAccount);

        LedgerEntry creditEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .accountId(targetAccount.getId())
                .direction(com.fooddelivery.common.enums.TransactionDirection.CREDIT)
                .amount(amount)
                .createdAt(LocalDateTime.now())
                .build();
        entryRepository.save(creditEntry);
        
        log.info("Recorded double entry transaction {} for amount {}", transactionId, amount);
    }

    private LedgerAccount getOrCreateAccount(UUID ownerId, AccountType ownerType) {
        return accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType)
                .orElseGet(() -> {
                    accountRepository.insertIfNotExists(UUID.randomUUID(), ownerId, ownerType.name());
                    return accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType)
                            .orElseThrow(() -> new IllegalStateException("Failed to get or create account concurrently"));
                });
    }
}
