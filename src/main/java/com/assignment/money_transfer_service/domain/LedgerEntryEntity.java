package com.assignment.money_transfer_service.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entry", indexes = {
    @Index(name = "idx_account_id", columnList = "account_id"),
    @Index(name = "idx_transfer_id", columnList = "transfer_id")
})
@Data
public class LedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private TransferEntity transfer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private EntryType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
