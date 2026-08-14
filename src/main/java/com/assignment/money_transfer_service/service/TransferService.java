package com.assignment.money_transfer_service.service;

import com.assignment.money_transfer_service.domain.AccountEntity;
import com.assignment.money_transfer_service.domain.AccountStatus;
import com.assignment.money_transfer_service.domain.EntryType;
import com.assignment.money_transfer_service.domain.TransferEntity;
import com.assignment.money_transfer_service.domain.TransferStatus;
import com.assignment.money_transfer_service.dto.response.TransferResponse;
import com.assignment.money_transfer_service.exception.AccountNotFoundException;
import com.assignment.money_transfer_service.exception.BusinessValidationException;
import com.assignment.money_transfer_service.exception.ConflictException;
import com.assignment.money_transfer_service.exception.IdempotencyConflictException;
import com.assignment.money_transfer_service.exception.RateLimitExceededException;
import com.assignment.money_transfer_service.exception.TransferException;
import com.assignment.money_transfer_service.repository.AccountRepository;
import com.assignment.money_transfer_service.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final RedisLockService redisLockService;
    private final RateLimitService rateLimitService;
    private final OutboxEventService outboxEventService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public TransferResponse initiateTransfer(String idempotencyKey, Long fromAccountId, Long toAccountId,
                                    BigDecimal amount, String currency) {
        validateTransferRequest(idempotencyKey, fromAccountId, toAccountId, amount, currency);
        
        if (!rateLimitService.isAllowed(fromAccountId)) {
            throw new RateLimitExceededException("Rate limit exceeded. Please try again later.");
        }
        
        String newRequestHash = generateRequestHash(idempotencyKey, fromAccountId, toAccountId, amount);
        
        java.util.Optional<TransferEntity> existingTransfer = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTransfer.isPresent()) {
            TransferEntity existing = existingTransfer.get();
            if (existing.getRequestHash().equals(newRequestHash)) {
                log.info("Returning existing transfer for idempotency key: {}", idempotencyKey);
                return toResponse(existing);
            } else {
                throw new IdempotencyConflictException("Idempotency key already used with different payload");
            }
        }
        
        TransferEntity transfer = executeTransfer(idempotencyKey, fromAccountId, toAccountId, amount, currency, newRequestHash);
        
        return toResponse(transfer);
    }

    private void validateTransferRequest(String idempotencyKey, Long fromAccountId, Long toAccountId, 
                                        BigDecimal amount, String currency) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessValidationException("Amount must be greater than zero");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new BusinessValidationException("Cannot transfer to the same account");
        }
    }

    private void validateTransferRules(AccountEntity fromAccount, AccountEntity toAccount, 
                                      BigDecimal amount, String currency) {
        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessValidationException("From account is not active");
        }
        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessValidationException("To account is not active");
        }
        if (!fromAccount.getCurrency().equals(currency)) {
            throw new BusinessValidationException("Currency mismatch for from account");
        }
        if (!toAccount.getCurrency().equals(currency)) {
            throw new BusinessValidationException("Currency mismatch for to account");
        }
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new BusinessValidationException("Insufficient balance");
        }
    }

    private String generateRequestHash(String idempotencyKey, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        return idempotencyKey + "-" + fromAccountId + "-" + toAccountId + "-" + amount.toString();
    }

    private TransferEntity executeTransfer(String idempotencyKey, Long fromAccountId, Long toAccountId,
                                        BigDecimal amount, String currency, String newRequestHash) {
        Long firstAccountId = Math.min(fromAccountId, toAccountId);
        Long secondAccountId = Math.max(fromAccountId, toAccountId);
        
        String firstLockToken = redisLockService.acquireLock(firstAccountId);
        if (firstLockToken == null) {
            throw new ConflictException("Account is currently being processed. Please try again.");
        }
        
        String secondLockToken = null;
        try {
            secondLockToken = redisLockService.acquireLock(secondAccountId);
            if (secondLockToken == null) {
                throw new ConflictException("Account is currently being processed. Please try again.");
            }
            
            AccountEntity fromAccount = accountRepository.findByIdForUpdate(fromAccountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromAccountId));
            AccountEntity toAccount = accountRepository.findByIdForUpdate(toAccountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + toAccountId));
            
            validateTransferRules(fromAccount, toAccount, amount, currency);
            
            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            toAccount.setBalance(toAccount.getBalance().add(amount));
            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);
            
            ledgerService.createLedgerEntry(
                    fromAccount,
                    null,
                    amount,
                    EntryType.DEBIT,
                    fromAccount.getBalance()
            );
            
            ledgerService.createLedgerEntry(
                    toAccount,
                    null,
                    amount,
                    EntryType.CREDIT,
                    toAccount.getBalance()
            );
            
            TransferEntity transfer = new TransferEntity();
            transfer.setIdempotencyKey(idempotencyKey);
            transfer.setFromAccount(fromAccount);
            transfer.setToAccount(toAccount);
            transfer.setAmount(amount);
            transfer.setCurrency(currency);
            transfer.setStatus(TransferStatus.COMPLETED);
            transfer.setRequestHash(newRequestHash);
            transfer.setCreatedAt(LocalDateTime.now());
            transfer = transferRepository.save(transfer);
            
            String eventPayload = String.format(
                "{\"eventId\":\"evt-%s\",\"eventType\":\"TransferCompleted\",\"transferId\":%d,\"fromAccountId\":%d,\"toAccountId\":%d,\"amount\":%s,\"currency\":\"%s\",\"occurredAt\":\"%s\"}",
                java.util.UUID.randomUUID().toString(),
                transfer.getId(),
                fromAccount.getId(),
                toAccount.getId(),
                transfer.getAmount().toString(),
                transfer.getCurrency(),
                java.time.Instant.now().toString()
            );
            outboxEventService.createOutboxEvent(
                "Transfer",
                transfer.getId().toString(),
                "TransferCompleted",
                eventPayload
            );
            
            redisTemplate.delete("accounts::" + fromAccountId);
            redisTemplate.delete("accounts::" + toAccountId);
            
            log.info("Transfer completed: {}", transfer.getId());
            return transfer;
        } catch (BusinessValidationException e) {
            log.error("Transfer failed business validation: {}", e.getMessage());
            throw e;
        } catch (AccountNotFoundException e) {
            log.error("Account not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Transfer failed: {}", e.getMessage(), e);
            throw new TransferException("Transfer failed", e);
        } finally {
            if (secondLockToken != null) {
                redisLockService.releaseLock(secondAccountId, secondLockToken);
            }
            redisLockService.releaseLock(firstAccountId, firstLockToken);
        }
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
                .status(transfer.getStatus().name())
                .fromAccountId(transfer.getFromAccount().getId())
                .toAccountId(transfer.getToAccount().getId())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency())
                .createdAt(transfer.getCreatedAt())
                .failureReason(transfer.getFailureReason())
                .build();
    }
}
