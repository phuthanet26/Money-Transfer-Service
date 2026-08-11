package com.assignment.money_transfer_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BalanceResponse {
    private Long accountId;
    private BigDecimal balance;
    private String currency;
    private LocalDateTime asOf;
}
