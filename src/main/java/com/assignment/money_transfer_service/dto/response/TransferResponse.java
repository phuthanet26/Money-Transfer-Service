package com.assignment.money_transfer_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransferResponse {

    private Long transferId;
    private String idempotencyKey;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
}
