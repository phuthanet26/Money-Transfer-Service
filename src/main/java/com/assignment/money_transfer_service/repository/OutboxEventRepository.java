package com.assignment.money_transfer_service.repository;

import com.assignment.money_transfer_service.domain.OutboxEventEntity;
import com.assignment.money_transfer_service.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}