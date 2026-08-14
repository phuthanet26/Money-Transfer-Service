package com.assignment.money_transfer_service.service;

import com.assignment.money_transfer_service.domain.AccountEntity;
import com.assignment.money_transfer_service.domain.AccountStatus;
import com.assignment.money_transfer_service.domain.EntryType;
import com.assignment.money_transfer_service.domain.LedgerEntryEntity;
import com.assignment.money_transfer_service.dto.response.AccountResponse;
import com.assignment.money_transfer_service.dto.response.BalanceResponse;
import com.assignment.money_transfer_service.dto.response.DepositResponse;
import com.assignment.money_transfer_service.dto.response.PagedTransactionResponse;
import com.assignment.money_transfer_service.dto.response.TransactionResponse;
import com.assignment.money_transfer_service.dto.response.WithdrawResponse;
import com.assignment.money_transfer_service.exception.AccountNotFoundException;
import com.assignment.money_transfer_service.exception.BusinessValidationException;
import com.assignment.money_transfer_service.exception.ConflictException;
import com.assignment.money_transfer_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final RedisLockService redisLockService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public AccountResponse createAccount(String ownerName, String currency, BigDecimal initialBalance) {
        validateAccountCreation(ownerName, currency, initialBalance);
        
        AccountEntity account = new AccountEntity();
        account.setAccountNumber(generateAccountNumber());
        account.setOwnerName(ownerName);
        account.setCurrency(currency);
        account.setBalance(initialBalance);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        
        AccountEntity savedAccount = accountRepository.save(account);
        
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.createLedgerEntry(
                    account,
                    null,
                    initialBalance,
                    EntryType.CREDIT,
                    savedAccount.getBalance()
            );
        }
        
        return toResponseWithCreatedAt(savedAccount);
    }

    private String generateAccountNumber() {
        Long maxId = accountRepository.findMaxId();
        long nextId = (maxId != null ? maxId : 0) + 1;
        return String.format("%010d", nextId);
    }

    private void validateAccountCreation(String ownerName, String currency, BigDecimal initialBalance) {
        if (ownerName == null || ownerName.isBlank()) {
            throw new BusinessValidationException("Owner name is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new BusinessValidationException("Currency is required");
        }
        if (currency.length() != 3) {
            throw new BusinessValidationException("Currency must be 3 characters");
        }
        if (initialBalance == null) {
            throw new BusinessValidationException("Initial balance is required");
        }
        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessValidationException("Initial balance must be non-negative");
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "accounts", key = "#accountId")
    public AccountResponse getAccountById(Long accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "accounts", key = "#accountNumber")
    public AccountResponse getAccountByNumber(String accountNumber) {
        AccountEntity account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        List<AccountEntity> accounts = accountRepository.findAll();
        return accounts.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
public AccountResponse updateAccountStatus(Long accountId, String status) {
        AccountEntity account = getAccountOrThrow(accountId);
        
        AccountStatus accountStatus = validateAndParseStatus(status);
        
        if (accountStatus == AccountStatus.CLOSED && account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException("Cannot close account with remaining balance");
        }
        
        account.setStatus(accountStatus);
        account.setUpdatedAt(LocalDateTime.now());
        AccountEntity updatedAccount = accountRepository.save(account);
        
        redisTemplate.delete("accounts::" + accountId);
        
        return toResponse(updatedAccount);
    }

    private AccountStatus validateAndParseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessValidationException("Status is required");
        }
        
        try {
            return AccountStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessValidationException("Invalid status. Valid values are: ACTIVE, FROZEN, CLOSED");
        }
    }

    @Transactional
    public DepositResponse deposit(Long accountId, BigDecimal amount) {
        String lockToken = redisLockService.acquireLock(accountId);
        if (lockToken == null) {
            throw new ConflictException("Account is currently being processed. Please try again.");
        }
        
        try {
            AccountEntity account = getActiveAccountWithLock(accountId);
            
            account.setBalance(account.getBalance().add(amount));
            account.setUpdatedAt(LocalDateTime.now());
            AccountEntity updatedAccount = accountRepository.save(account);
            
            LedgerEntryEntity ledgerEntry = ledgerService.createLedgerEntry(
                    account,
                    null,
                    amount,
                    EntryType.CREDIT,
                    updatedAccount.getBalance()
            );
            
            redisTemplate.delete("accounts::" + accountId);
            
            return DepositResponse.builder()
                    .accountId(updatedAccount.getId())
                    .balance(updatedAccount.getBalance())
                    .ledgerEntryId(ledgerEntry.getId())
                    .build();
        } finally {
            redisLockService.releaseLock(accountId, lockToken);
        }
    }

    @Transactional
    public WithdrawResponse withdraw(Long accountId, BigDecimal amount) {
        String lockToken = redisLockService.acquireLock(accountId);
        if (lockToken == null) {
            throw new ConflictException("Account is currently being processed. Please try again.");
        }
        
        try {
            AccountEntity account = getActiveAccountWithLock(accountId);
            
            if (account.getBalance().compareTo(amount) < 0) {
                throw new BusinessValidationException("Insufficient balance");
            }
            
            account.setBalance(account.getBalance().subtract(amount));
            account.setUpdatedAt(LocalDateTime.now());
            AccountEntity updatedAccount = accountRepository.save(account);
            
            LedgerEntryEntity ledgerEntry = ledgerService.createLedgerEntry(
                    account,
                    null,
                    amount,
                    EntryType.DEBIT,
                    updatedAccount.getBalance()
            );
            
            redisTemplate.delete("accounts::" + accountId);
            
            return WithdrawResponse.builder()
                    .accountId(updatedAccount.getId())
                    .balance(updatedAccount.getBalance())
                    .ledgerEntryId(ledgerEntry.getId())
                    .build();
        } finally {
            redisLockService.releaseLock(accountId, lockToken);
        }
    }

    private AccountEntity getActiveAccountWithLock(Long accountId) {
        AccountEntity account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessValidationException("Account is not active");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId) {
        AccountEntity account = getAccountOrThrow(accountId);
        return BalanceResponse.builder()
                .accountId(account.getId())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .asOf(LocalDateTime.now())
                .build();
    }

    @Transactional(readOnly = true)
    public PagedTransactionResponse getTransactions(Long accountId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }
        
        getAccountOrThrow(accountId);
        
        List<LedgerEntryEntity> ledgerEntries = ledgerService.getLedgerEntriesByAccount(accountId);
        
        int start = page * size;
        int end = Math.min(start + size, ledgerEntries.size());
        
        List<TransactionResponse> items;
        if (start >= ledgerEntries.size()) {
            items = List.of();
        } else {
            items = ledgerEntries.subList(start, end).stream()
                    .map(this::toTransactionResponse)
                    .toList();
        }
        
        int totalElements = ledgerEntries.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        
        return PagedTransactionResponse.builder()
                .accountId(accountId)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .items(items)
                .build();
    }

    private TransactionResponse toTransactionResponse(LedgerEntryEntity entry) {
        return TransactionResponse.builder()
                .id(entry.getId())
                .entryType(entry.getType().name())
                .amount(entry.getAmount())
                .balanceAfter(entry.getBalanceAfter())
                .transferId(entry.getTransfer() != null ? entry.getTransfer().getId() : null)
                .createdAt(entry.getCreatedAt())
                .build();
    }

    private AccountEntity getAccountOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    private AccountResponse toResponse(AccountEntity account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .ownerName(account.getOwnerName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus().name())
                .build();
    }

    private AccountResponse toResponseWithCreatedAt(AccountEntity account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .ownerName(account.getOwnerName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus().name())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
