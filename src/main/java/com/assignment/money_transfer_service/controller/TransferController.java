package com.assignment.money_transfer_service.controller;

import com.assignment.money_transfer_service.dto.request.TransferRequest;
import com.assignment.money_transfer_service.dto.response.TransferResponse;
import com.assignment.money_transfer_service.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<TransferResponse> initiateTransfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.initiateTransfer(
                idempotencyKey,
                request.getFromAccountId(),
                request.getToAccountId(),
                request.getAmount(),
                request.getCurrency()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/transfers/" + response.getTransferId())
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransferById(@PathVariable Long id) {
        TransferResponse response = transferService.getTransferById(id);
        return ResponseEntity.ok(response);
    }
}
