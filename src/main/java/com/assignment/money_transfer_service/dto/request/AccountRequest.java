package com.assignment.money_transfer_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountRequest {

    @NotBlank(message = "Account number is required")
    @Size(max = 20, message = "Account number must not exceed 20 characters")
    private String accountNumber;

    @NotBlank(message = "Owner name is required")
    @Size(max = 120, message = "Owner name must not exceed 120 characters")
    private String ownerName;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;
}
