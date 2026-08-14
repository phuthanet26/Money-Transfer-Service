package com.assignment.money_transfer_service.controller;

import com.assignment.money_transfer_service.dto.request.AccountRequest;
import com.assignment.money_transfer_service.dto.request.AccountStatusRequest;
import com.assignment.money_transfer_service.dto.request.DepositRequest;
import com.assignment.money_transfer_service.dto.request.WithdrawRequest;
import com.assignment.money_transfer_service.dto.response.AccountResponse;
import com.assignment.money_transfer_service.dto.response.BalanceResponse;
import com.assignment.money_transfer_service.dto.response.DepositResponse;
import com.assignment.money_transfer_service.dto.response.PagedTransactionResponse;
import com.assignment.money_transfer_service.dto.response.WithdrawResponse;
import com.assignment.money_transfer_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountRequest request) {
        AccountResponse response = accountService.createAccount(
                request.getOwnerName(),
                request.getCurrency(),
                request.getInitialBalance()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/accounts/" + response.getId())
                .body(response);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long accountId) {
        AccountResponse response = accountService.getAccountById(accountId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{accountId}/status")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountStatusRequest request) {
        AccountResponse response = accountService.updateAccountStatus(accountId, request.getStatus());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<DepositResponse> deposit(
            @PathVariable Long accountId,
            @Valid @RequestBody DepositRequest request) {
        DepositResponse response = accountService.deposit(accountId, request.getAmount());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<WithdrawResponse> withdraw(
            @PathVariable Long accountId,
            @Valid @RequestBody WithdrawRequest request) {
        WithdrawResponse response = accountService.withdraw(accountId, request.getAmount());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long accountId) {
        BalanceResponse response = accountService.getBalance(accountId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<PagedTransactionResponse> getTransactions(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedTransactionResponse response = accountService.getTransactions(accountId, page, size);
        return ResponseEntity.ok(response);
    }
}
