package com.assignment.money_transfer_service.service;

import com.assignment.money_transfer_service.domain.AccountEntity;
import com.assignment.money_transfer_service.domain.AccountStatus;
import com.assignment.money_transfer_service.domain.EntryType;
import com.assignment.money_transfer_service.domain.LedgerEntryEntity;
import com.assignment.money_transfer_service.dto.response.DepositResponse;
import com.assignment.money_transfer_service.dto.response.WithdrawResponse;
import com.assignment.money_transfer_service.exception.BusinessValidationException;
import com.assignment.money_transfer_service.exception.ConflictException;
import com.assignment.money_transfer_service.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private RedisLockService redisLockService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private AccountService accountService;

    private AccountEntity testAccount;
    private LedgerEntryEntity ledgerEntry;

    @BeforeEach
    void setUp() {
        testAccount = new AccountEntity();
        testAccount.setId(1L);
        testAccount.setAccountNumber("0000001001");
        testAccount.setOwnerName("Test User");
        testAccount.setCurrency("THB");
        testAccount.setBalance(new BigDecimal("1000.00"));
        testAccount.setStatus(AccountStatus.ACTIVE);
        testAccount.setCreatedAt(LocalDateTime.now());
        testAccount.setUpdatedAt(LocalDateTime.now());

        ledgerEntry = new LedgerEntryEntity();
        ledgerEntry.setId(100L);
        ledgerEntry.setAccount(testAccount);
        ledgerEntry.setAmount(new BigDecimal("500.00"));
        ledgerEntry.setType(EntryType.CREDIT);
        ledgerEntry.setBalanceAfter(new BigDecimal("1500.00"));
        ledgerEntry.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void deposit_ShouldAddAmountToBalance_WhenAccountIsActive() {
        BigDecimal depositAmount = new BigDecimal("500.00");
        when(redisLockService.acquireLock(1L)).thenReturn("token");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(AccountEntity.class))).thenReturn(testAccount);
        when(ledgerService.createLedgerEntry(any(), any(), any(), any(), any())).thenReturn(ledgerEntry);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        DepositResponse response = accountService.deposit(1L, depositAmount);

        assertNotNull(response);
        assertEquals(1L, response.getAccountId());
        assertEquals(100L, response.getLedgerEntryId());
        verify(accountRepository).save(any(AccountEntity.class));
        verify(redisLockService).releaseLock(1L, "token");
    }

    @Test
    void deposit_ShouldThrowConflictException_WhenLockCannotBeAcquired() {
        BigDecimal depositAmount = new BigDecimal("500.00");
        when(redisLockService.acquireLock(1L)).thenReturn(null);

        assertThrows(ConflictException.class, () -> accountService.deposit(1L, depositAmount));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void withdraw_ShouldDeductAmountFromBalance_WhenSufficientFunds() {
        BigDecimal withdrawAmount = new BigDecimal("500.00");
        when(redisLockService.acquireLock(1L)).thenReturn("token");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(AccountEntity.class))).thenReturn(testAccount);
        when(ledgerService.createLedgerEntry(any(), any(), any(), any(), any())).thenReturn(ledgerEntry);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        WithdrawResponse response = accountService.withdraw(1L, withdrawAmount);

        assertNotNull(response);
        assertEquals(1L, response.getAccountId());
        assertEquals(100L, response.getLedgerEntryId());
        verify(accountRepository).save(any(AccountEntity.class));
        verify(redisLockService).releaseLock(1L, "token");
    }

    @Test
    void withdraw_ShouldThrowBusinessValidationException_WhenInsufficientFunds() {
        BigDecimal withdrawAmount = new BigDecimal("1500.00");
        when(redisLockService.acquireLock(1L)).thenReturn("token");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));

        assertThrows(BusinessValidationException.class, () -> accountService.withdraw(1L, withdrawAmount));
        verify(accountRepository, never()).save(any());
        verify(redisLockService).releaseLock(1L, "token");
    }

    @Test
    void withdraw_ShouldThrowConflictException_WhenLockCannotBeAcquired() {
        BigDecimal withdrawAmount = new BigDecimal("500.00");
        when(redisLockService.acquireLock(1L)).thenReturn(null);

        assertThrows(ConflictException.class, () -> accountService.withdraw(1L, withdrawAmount));
        verify(accountRepository, never()).save(any());
    }
}
