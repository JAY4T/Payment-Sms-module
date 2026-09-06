package com.module.paymentsms.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Without this, a malformed request body (e.g. an invalid enum value) only got as far as
// Spring's own DefaultHandlerExceptionResolver, which sends a bare {timestamp,status,error,path}
// body with no indication of what was actually wrong - and, before the /error permitAll fix in
// SecurityConfig, an empty 403 instead of even that. Every error response should instead say
// specifically what was invalid and, for enums, what the accepted values actually are.
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final BuildResponse buildResponse;

    public GlobalExceptionHandler(BuildResponse buildResponse) {
        this.buildResponse = buildResponse;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleUnreadableBody(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();
        log.warn("DEBUG cause class: {}", cause == null ? "null" : cause.getClass().getName());
        if (cause instanceof InvalidFormatException invalidFormat && invalidFormat.getTargetType().isEnum()) {
            String field = invalidFormat.getPath().isEmpty()
                    ? "value"
                    : invalidFormat.getPath().get(invalidFormat.getPath().size() - 1).getFieldName();
            String invalidValue = String.valueOf(invalidFormat.getValue());
            String validValues = Arrays.stream(invalidFormat.getTargetType().getEnumConstants())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            log.warn("Invalid value for field '{}': '{}'", field, invalidValue);
            return buildResponse.error(
                    "Invalid " + field + ": '" + invalidValue + "'. Accepted values: " + validValues,
                    null,
                    HttpStatus.BAD_REQUEST
            );
        }
        log.warn("Malformed request body: {}", e.getMessage());
        return buildResponse.error("Malformed request body: " + e.getMostSpecificCause().getMessage(), null, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationErrors(MethodArgumentNotValidException e) {
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return buildResponse.error("Validation failed", fieldErrors, HttpStatus.BAD_REQUEST);
    }
}
