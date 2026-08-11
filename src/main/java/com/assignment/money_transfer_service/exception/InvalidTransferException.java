package com.assignment.money_transfer_service.exception;

public class InvalidTransferException extends RuntimeException {
    
    public InvalidTransferException(String message) {
        super(message);
    }
    
    public InvalidTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
