package com.fooddelivery.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

import com.fooddelivery.common.enums.AccountType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "ledger_accounts", uniqueConstraints = {
    @jakarta.persistence.UniqueConstraint(columnNames = {"ownerId", "ownerType"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerAccount {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private AccountType ownerType;
    private UUID ownerId;
    
    private BigDecimal balance;

    @Version
    private Integer lockVersion;
}
