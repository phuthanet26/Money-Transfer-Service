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
import com.assignment.money_transfer_service.repository.AccountRepository;
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
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    public AccountResponse createAccount(String accountNumber, String ownerName, String currency) {
        validateAccountCreation(accountNumber, ownerName, currency);
        
        AccountEntity account = new AccountEntity();
        account.setAccountNumber(accountNumber);
        account.setOwnerName(ownerName);
        account.setCurrency(currency);
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        
        AccountEntity savedAccount = accountRepository.save(account);
        return toResponse(savedAccount);
    }

    private void validateAccountCreation(String accountNumber, String ownerName, String currency) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("Account number is required");
        }
        if (ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("Owner name is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
        if (accountRepository.existsByAccountNumber(accountNumber)) {
            throw new IllegalArgumentException("Account number already exists: " + accountNumber);
        }
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
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

    public AccountResponse updateAccountStatus(Long accountId, AccountStatus status) {
        AccountEntity account = getAccountOrThrow(accountId);
        account.setStatus(status);
        account.setUpdatedAt(LocalDateTime.now());
        AccountEntity updatedAccount = accountRepository.save(account);
        return toResponse(updatedAccount);
    }

    public DepositResponse deposit(Long accountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        AccountEntity account = getActiveAccount(accountId);
        
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
        
        return DepositResponse.builder()
                .accountId(updatedAccount.getId())
                .balance(updatedAccount.getBalance())
                .ledgerEntryId(ledgerEntry.getId())
                .build();
    }

    public WithdrawResponse withdraw(Long accountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        AccountEntity account = getActiveAccount(accountId);
        
        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
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
        
        return WithdrawResponse.builder()
                .accountId(updatedAccount.getId())
                .balance(updatedAccount.getBalance())
                .ledgerEntryId(ledgerEntry.getId())
                .build();
    }

    private AccountEntity getActiveAccount(Long accountId) {
        AccountEntity account = getAccountOrThrow(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active: " + accountId);
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
        
        AccountEntity account = getAccountOrThrow(accountId);
        
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
                .createdAt(entry.getCreatedAt())
                .build();
    }

    private AccountEntity getAccountOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    private AccountResponse toResponse(AccountEntity account) {
        return AccountResponse.builder()
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getOwnerName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus().name())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
