package com.assignment.money_transfer_service.controller;

import com.assignment.money_transfer_service.domain.AccountStatus;
import com.assignment.money_transfer_service.dto.request.AccountRequest;
import com.assignment.money_transfer_service.dto.response.AccountResponse;
import com.assignment.money_transfer_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountRequest request) {
        AccountResponse response = accountService.createAccount(
                request.getAccountNumber(),
                request.getOwnerName(),
                request.getCurrency()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long accountId) {
        AccountResponse response = accountService.getAccountById(accountId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccountByNumber(@PathVariable String accountNumber) {
        AccountResponse response = accountService.getAccountByNumber(accountNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> responses = accountService.getAllAccounts();
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{accountId}/status")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable Long accountId,
            @RequestParam AccountStatus status) {
        AccountResponse response = accountService.updateAccountStatus(accountId, status);
        return ResponseEntity.ok(response);
    }
}
