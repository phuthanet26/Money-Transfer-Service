package com.assignment.money_transfer_service.dto.request;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AccountRequest {

    private String ownerName;
    private String currency;
    private BigDecimal initialBalance;
}
