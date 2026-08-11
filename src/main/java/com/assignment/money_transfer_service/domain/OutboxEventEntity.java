package com.assignment.money_transfer_service.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Data
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false, length = 50)
    private String aggregateId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OutboxStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime publishedAt;
}
