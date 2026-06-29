package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.Ledger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ILedgerRepository extends JpaRepository<Ledger, UUID> {
    List<Ledger> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
    
    Optional<Ledger> findFirstByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
