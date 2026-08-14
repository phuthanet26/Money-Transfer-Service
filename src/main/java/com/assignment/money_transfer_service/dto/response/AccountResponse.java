package com.assignment.money_transfer_service.dto.response;

import com.assignment.money_transfer_service.domain.AccountEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AccountResponse {

    private Long id;
    private String accountNumber;
    private String ownerName;
    private BigDecimal balance;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
}
