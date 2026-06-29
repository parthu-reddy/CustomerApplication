package com.fooddelivery.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledgers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ledger {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private UUID accountId; // e.g. Restaurant ID or Customer ID
    private String transactionRef; // e.g. Order ID
    
    private String type; // CREDIT or DEBIT
    private BigDecimal amount;
    private BigDecimal balanceAfter;

    @Version
    private Long version; // Optimistic locking
    
    private LocalDateTime createdAt;
}
