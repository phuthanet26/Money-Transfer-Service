package com.assignment.money_transfer_service.dto.response;

import com.assignment.money_transfer_service.domain.AccountEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AccountResponse {

    private Long accountId;
    private String accountNumber;
    private String accountName;
    private BigDecimal balance;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AccountResponse fromEntity(AccountEntity account) {
        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus().name())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
