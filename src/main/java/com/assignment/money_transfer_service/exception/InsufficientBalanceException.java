package com.assignment.money_transfer_service.exception;

public class InsufficientBalanceException extends RuntimeException {
    
    public InsufficientBalanceException(String message) {
        super(message);
    }
    
    public InsufficientBalanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
