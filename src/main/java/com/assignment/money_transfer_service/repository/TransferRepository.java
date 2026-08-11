package com.assignment.money_transfer_service.repository;

import com.assignment.money_transfer_service.domain.TransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransferRepository extends JpaRepository<TransferEntity, Long> {

    Optional<TransferEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<TransferEntity> findByFromAccount_IdOrderByCreatedAtDesc(Long accountId);

    List<TransferEntity> findByToAccount_IdOrderByCreatedAtDesc(Long accountId);
}
