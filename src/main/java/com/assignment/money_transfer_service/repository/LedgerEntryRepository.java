package com.assignment.money_transfer_service.repository;

import com.assignment.money_transfer_service.domain.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, Long> {

    List<LedgerEntryEntity> findByAccount_IdOrderByCreatedAtDesc(Long accountId);

    List<LedgerEntryEntity> findByTransfer_IdOrderByCreatedAtAsc(Long transferId);

    @Query("SELECT le FROM LedgerEntryEntity le WHERE le.account.id = :accountId " +
           "AND le.createdAt BETWEEN :startDate AND :endDate ORDER BY le.createdAt DESC")
    List<LedgerEntryEntity> findByAccountIdAndDateRange(@Param("accountId") Long accountId,
                                                   @Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);
}
