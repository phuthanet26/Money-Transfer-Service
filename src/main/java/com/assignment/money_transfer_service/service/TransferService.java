package com.assignment.money_transfer_service.service;

import com.assignment.money_transfer_service.domain.AccountEntity;
import com.assignment.money_transfer_service.domain.AccountStatus;
import com.assignment.money_transfer_service.domain.EntryType;
import com.assignment.money_transfer_service.domain.TransferEntity;
import com.assignment.money_transfer_service.domain.TransferStatus;
import com.assignment.money_transfer_service.dto.response.TransferResponse;
import com.assignment.money_transfer_service.exception.AccountNotFoundException;
import com.assignment.money_transfer_service.exception.TransferException;
import com.assignment.money_transfer_service.repository.AccountRepository;
import com.assignment.money_transfer_service.repository.TransferRepository;
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
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    public TransferResponse initiateTransfer(String idempotencyKey, Long fromAccountId, Long toAccountId,
                                    BigDecimal amount, String currency) {
        validateTransferRequest(idempotencyKey, fromAccountId, toAccountId, amount, currency);
        
        AccountEntity fromAccount = getActiveAccount(fromAccountId);
        AccountEntity toAccount = getActiveAccount(toAccountId);
        
        validateTransferRules(fromAccount, toAccount, amount, currency);
        
        TransferEntity transfer = createTransfer(idempotencyKey, fromAccount, toAccount, amount, currency);
        executeTransfer(transfer);
        
        return toResponse(transfer);
    }

    private void validateTransferRequest(String idempotencyKey, Long fromAccountId, Long toAccountId, 
                                        BigDecimal amount, String currency) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (fromAccountId == null) {
            throw new IllegalArgumentException("From account ID is required");
        }
        if (toAccountId == null) {
            throw new IllegalArgumentException("To account ID is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
    }

    private AccountEntity getActiveAccount(Long accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active: " + accountId);
        }
        
        return account;
    }

    private void validateTransferRules(AccountEntity fromAccount, AccountEntity toAccount, 
                                      BigDecimal amount, String currency) {
        if (!fromAccount.getCurrency().equals(currency)) {
            throw new IllegalArgumentException("Currency mismatch for from account");
        }
        if (!toAccount.getCurrency().equals(currency)) {
            throw new IllegalArgumentException("Currency mismatch for to account");
        }
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }
    }

    private TransferEntity createTransfer(String idempotencyKey, AccountEntity fromAccount, AccountEntity toAccount, 
                                   BigDecimal amount, String currency) {
        TransferEntity transfer = new TransferEntity();
        transfer.setIdempotencyKey(idempotencyKey);
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setAmount(amount);
        transfer.setCurrency(currency);
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setRequestHash(generateRequestHash(idempotencyKey, fromAccount.getId(), toAccount.getId(), amount));
        transfer.setCreatedAt(LocalDateTime.now());
        
        return transferRepository.save(transfer);
    }

    private String generateRequestHash(String idempotencyKey, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        return idempotencyKey + "-" + fromAccountId + "-" + toAccountId + "-" + amount.toString();
    }

    private void executeTransfer(TransferEntity transfer) {
        try {
            AccountEntity fromAccount = lockAccount(transfer.getFromAccount().getId());
            AccountEntity toAccount = lockAccount(transfer.getToAccount().getId());
            
            updateBalances(fromAccount, toAccount, transfer.getAmount());
            recordLedgerEntries(fromAccount, toAccount, transfer);
            
            transfer.setStatus(TransferStatus.COMPLETED);
            transferRepository.save(transfer);
            
            log.info("Transfer completed: {}", transfer.getId());
        } catch (Exception e) {
            log.error("Transfer failed: {}", transfer.getId(), e);
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason(e.getMessage());
            transferRepository.save(transfer);
            throw new TransferException("Transfer failed", e);
        }
    }

    private AccountEntity lockAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    private void updateBalances(AccountEntity fromAccount, AccountEntity toAccount, BigDecimal amount) {
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }

    private void recordLedgerEntries(AccountEntity fromAccount, AccountEntity toAccount, TransferEntity transfer) {
        ledgerService.createLedgerEntry(
                fromAccount,
                transfer,
                transfer.getAmount(),
                EntryType.DEBIT,
                fromAccount.getBalance()
        );
        
        ledgerService.createLedgerEntry(
                toAccount,
                transfer,
                transfer.getAmount(),
                EntryType.CREDIT,
                toAccount.getBalance()
        );
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransferById(Long id) {
        TransferEntity transfer = transferRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Transfer not found: " + id));
        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransferByIdempotencyKey(String idempotencyKey) {
        TransferEntity transfer = transferRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new AccountNotFoundException("Transfer not found: " + idempotencyKey));
        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> getTransfersByAccount(Long accountId) {
        List<TransferEntity> transfers = transferRepository.findByFromAccount_IdOrderByCreatedAtDesc(accountId);
        return transfers.stream()
                .map(this::toResponse)
                .toList();
    }

    private TransferResponse toResponse(TransferEntity transfer) {
        return TransferResponse.builder()
                .transferId(transfer.getId())
                .idempotencyKey(transfer.getIdempotencyKey())
                .fromAccountId(transfer.getFromAccount().getId())
                .toAccountId(transfer.getToAccount().getId())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency())
                .status(transfer.getStatus().name())
                .createdAt(transfer.getCreatedAt())
                .build();
    }
}
