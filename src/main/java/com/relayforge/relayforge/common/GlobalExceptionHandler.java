package com.relayforge.relayforge.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({EndpointNotFoundException.class, EventNotFoundException.class,
            SubscriptionNotFoundException.class, DeliveryNotFoundException.class})
    public ResponseEntity<ApiError> notFound(RuntimeException ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }
    @ExceptionHandler({DuplicateSubscriptionException.class, DuplicateEventKeyException.class})
    public ResponseEntity<ApiError> conflict(RuntimeException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, ex.getMessage(), request);
    }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class,
            MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> badRequest(Exception ex, HttpServletRequest request) {
        String message = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().map(e -> e.getField()+": "+e.getDefaultMessage()).findFirst().orElse("Invalid request")
                : ex.getMessage();
        return response(HttpStatus.BAD_REQUEST, message, request);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internal(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred", request);
    }
    private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
    public record ApiError(OffsetDateTime timestamp, int status, String error, String message, String path) {}
}
