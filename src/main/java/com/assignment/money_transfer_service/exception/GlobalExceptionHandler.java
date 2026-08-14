package com.assignment.money_transfer_service.exception;

import com.assignment.money_transfer_service.dto.response.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BASE_ERROR_URL = "https://errors.bank.local/";

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ProblemDetails> handleAccountNotFoundException(AccountNotFoundException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type(BASE_ERROR_URL + "account-not-found")
                .title("Account Not Found")
                .status(HttpStatus.NOT_FOUND.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .traceId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(TransferException.class)
    public ResponseEntity<ProblemDetails> handleTransferException(TransferException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type(BASE_ERROR_URL + "transfer-failed")
                .title("Transfer Failed")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .traceId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetails> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type(BASE_ERROR_URL + "invalid-request")
                .title("Invalid Request")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .traceId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetails> handleRateLimitExceededException(RateLimitExceededException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type(BASE_ERROR_URL + "rate-limit-exceeded")
                .title("Rate Limit Exceeded")
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .traceId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetails> handleIdempotencyConflictException(IdempotencyConflictException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type(BASE_ERROR_URL + "idempotency-conflict")
                .title("Idempotency Conflict")
                .status(HttpStatus.CONFLICT.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .traceId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetails> handleConflictException(ConflictException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type(BASE_ERROR_URL + "conflict")
                .title("Conflict")
                .status(HttpStatus.CONFLICT.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .traceId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type(BASE_ERROR_URL + "validation-error")
                .title("Validation Error")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("Invalid request parameters")
                .instance(request.getRequestURI())
                .traceId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetails> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type(BASE_ERROR_URL + "invalid-json")
                .title("Invalid JSON Format")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("Invalid request body format")
                .instance(request.getRequestURI())
                .traceId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetails> handleMissingRequestHeaderException(MissingRequestHeaderException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type(BASE_ERROR_URL + "missing-header")
                .title("Missing Required Header")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("Missing required header: " + ex.getHeaderName())
                .instance(request.getRequestURI())
                .traceId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<ProblemDetails> handleBusinessValidationException(BusinessValidationException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type(BASE_ERROR_URL + "business-validation-error")
                .title("Business Validation Error")
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .traceId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGlobalException(Exception ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type(BASE_ERROR_URL + "internal-error")
                .title("Internal Server Error")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .detail("An unexpected error occurred")
                .instance(request.getRequestURI())
                .traceId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
