package com.assignment.money_transfer_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WithdrawResponse {
    private Long accountId;
    private BigDecimal balance;
    private Long ledgerEntryId;
}
