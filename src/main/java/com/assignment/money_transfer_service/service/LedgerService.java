package com.assignment.money_transfer_service.service;

import com.assignment.money_transfer_service.domain.AccountEntity;
import com.assignment.money_transfer_service.domain.EntryType;
import com.assignment.money_transfer_service.domain.LedgerEntryEntity;
import com.assignment.money_transfer_service.domain.TransferEntity;
import com.assignment.money_transfer_service.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerEntryEntity createLedgerEntry(AccountEntity account, TransferEntity transfer, BigDecimal amount,
                                        EntryType type, BigDecimal balanceAfter) {
        LedgerEntryEntity entry = new LedgerEntryEntity();
        entry.setAccount(account);
        entry.setTransfer(transfer);
        entry.setAmount(amount);
        entry.setType(type);
        entry.setBalanceAfter(balanceAfter);
        entry.setCreatedAt(LocalDateTime.now());
        
        return ledgerEntryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryEntity> getLedgerEntriesByAccount(Long accountId) {
        return ledgerEntryRepository.findByAccount_IdOrderByCreatedAtDesc(accountId);
    }
}
