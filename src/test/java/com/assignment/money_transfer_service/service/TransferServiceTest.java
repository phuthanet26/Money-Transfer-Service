package com.assignment.money_transfer_service.service;

import com.assignment.money_transfer_service.domain.AccountEntity;
import com.assignment.money_transfer_service.domain.AccountStatus;
import com.assignment.money_transfer_service.domain.EntryType;
import com.assignment.money_transfer_service.domain.LedgerEntryEntity;
import com.assignment.money_transfer_service.domain.TransferEntity;
import com.assignment.money_transfer_service.dto.response.TransferResponse;
import com.assignment.money_transfer_service.exception.BusinessValidationException;
import com.assignment.money_transfer_service.exception.ConflictException;
import com.assignment.money_transfer_service.exception.RateLimitExceededException;
import com.assignment.money_transfer_service.repository.AccountRepository;
import com.assignment.money_transfer_service.repository.TransferRepository;
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
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private RedisLockService redisLockService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private TransferService transferService;

    private AccountEntity fromAccount;
    private AccountEntity toAccount;
    private LedgerEntryEntity ledgerEntry;

    @BeforeEach
    void setUp() {
        fromAccount = new AccountEntity();
        fromAccount.setId(1L);
        fromAccount.setAccountNumber("0000001001");
        fromAccount.setOwnerName("From User");
        fromAccount.setCurrency("THB");
        fromAccount.setBalance(new BigDecimal("5000.00"));
        fromAccount.setStatus(AccountStatus.ACTIVE);
        fromAccount.setCreatedAt(LocalDateTime.now());
        fromAccount.setUpdatedAt(LocalDateTime.now());

        toAccount = new AccountEntity();
        toAccount.setId(2L);
        toAccount.setAccountNumber("0000001002");
        toAccount.setOwnerName("To User");
        toAccount.setCurrency("THB");
        toAccount.setBalance(new BigDecimal("3000.00"));
        toAccount.setStatus(AccountStatus.ACTIVE);
        toAccount.setCreatedAt(LocalDateTime.now());
        toAccount.setUpdatedAt(LocalDateTime.now());

        ledgerEntry = new LedgerEntryEntity();
        ledgerEntry.setId(200L);
        ledgerEntry.setAccount(fromAccount);
        ledgerEntry.setAmount(new BigDecimal("1000.00"));
        ledgerEntry.setType(EntryType.DEBIT);
        ledgerEntry.setBalanceAfter(new BigDecimal("4000.00"));
        ledgerEntry.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void initiateTransfer_ShouldSucceed_WhenValidTransfer() {
        String idempotencyKey = "test-key-123";
        BigDecimal amount = new BigDecimal("1000.00");
        String currency = "THB";

        when(rateLimitService.isAllowed(1L)).thenReturn(true);
        when(redisLockService.acquireLock(1L)).thenReturn("token1");
        when(redisLockService.acquireLock(2L)).thenReturn("token2");
        when(transferRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(toAccount));
        when(transferRepository.save(any(TransferEntity.class))).thenAnswer(invocation -> {
            TransferEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });
        when(ledgerService.createLedgerEntry(any(), any(), any(), any(), any())).thenReturn(ledgerEntry);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        TransferResponse response = transferService.initiateTransfer(idempotencyKey, 1L, 2L, amount, currency);

        assertNotNull(response);
        assertEquals(100L, response.getTransferId());
        assertEquals("COMPLETED", response.getStatus());
        verify(transferRepository).save(any(TransferEntity.class));
        verify(redisLockService).releaseLock(1L, "token1");
        verify(redisLockService).releaseLock(2L, "token2");
    }

    @Test
    void initiateTransfer_ShouldThrowBusinessValidationException_WhenTransferToSelf() {
        String idempotencyKey = "test-key-123";
        BigDecimal amount = new BigDecimal("1000.00");
        String currency = "THB";

        assertThrows(BusinessValidationException.class, 
            () -> transferService.initiateTransfer(idempotencyKey, 1L, 1L, amount, currency));
    }

    @Test
    void initiateTransfer_ShouldThrowBusinessValidationException_WhenCurrencyMismatch() {
        String idempotencyKey = "test-key-123";
        BigDecimal amount = new BigDecimal("1000.00");
        String currency = "USD";

        when(rateLimitService.isAllowed(1L)).thenReturn(true);
        when(redisLockService.acquireLock(1L)).thenReturn("token1");
        when(redisLockService.acquireLock(2L)).thenReturn("token2");
        when(transferRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(toAccount));

        assertThrows(BusinessValidationException.class, 
            () -> transferService.initiateTransfer(idempotencyKey, 1L, 2L, amount, currency));
        verify(redisLockService).releaseLock(1L, "token1");
        verify(redisLockService).releaseLock(2L, "token2");
    }

    @Test
    void initiateTransfer_ShouldThrowRateLimitExceededException_WhenRateLimitExceeded() {
        String idempotencyKey = "test-key-123";
        BigDecimal amount = new BigDecimal("1000.00");
        String currency = "THB";

        when(rateLimitService.isAllowed(1L)).thenReturn(false);

        assertThrows(RateLimitExceededException.class, 
            () -> transferService.initiateTransfer(idempotencyKey, 1L, 2L, amount, currency));
    }

    @Test
    void initiateTransfer_ShouldThrowBusinessValidationException_WhenInsufficientFunds() {
        String idempotencyKey = "test-key-123";
        BigDecimal amount = new BigDecimal("10000.00");
        String currency = "THB";

        when(rateLimitService.isAllowed(1L)).thenReturn(true);
        when(redisLockService.acquireLock(1L)).thenReturn("token1");
        when(redisLockService.acquireLock(2L)).thenReturn("token2");
        when(transferRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(toAccount));

        assertThrows(BusinessValidationException.class, 
            () -> transferService.initiateTransfer(idempotencyKey, 1L, 2L, amount, currency));
        verify(redisLockService).releaseLock(1L, "token1");
        verify(redisLockService).releaseLock(2L, "token2");
    }

    @Test
    void initiateTransfer_ShouldThrowConflictException_WhenLockCannotBeAcquired() {
        String idempotencyKey = "test-key-123";
        BigDecimal amount = new BigDecimal("1000.00");
        String currency = "THB";

        when(rateLimitService.isAllowed(1L)).thenReturn(true);
        when(redisLockService.acquireLock(1L)).thenReturn(null);

        assertThrows(ConflictException.class, 
            () -> transferService.initiateTransfer(idempotencyKey, 1L, 2L, amount, currency));
    }
}
