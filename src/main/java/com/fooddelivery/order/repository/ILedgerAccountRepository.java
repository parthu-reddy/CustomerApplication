package com.fooddelivery.order.repository;

import com.fooddelivery.order.entity.LedgerAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ILedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    @Lock(LockModeType.OPTIMISTIC)
    Optional<LedgerAccount> findByOwnerIdAndOwnerType(UUID ownerId, String ownerType);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "INSERT INTO ledger_accounts (id, owner_id, owner_type, balance, lock_version) VALUES (:id, :ownerId, :ownerType, 0, 0) ON CONFLICT (owner_id, owner_type) DO NOTHING", nativeQuery = true)
    int insertIfNotExists(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("ownerId") UUID ownerId, @org.springframework.data.repository.query.Param("ownerType") String ownerType);
}
