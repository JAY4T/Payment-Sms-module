package com.module.paymentsms.controller;

import com.module.paymentsms.config.BuildResponse;
import com.module.paymentsms.dto.PaginationDto;
import com.module.paymentsms.dto.TransactionCallbackDto;
import com.module.paymentsms.dto.TransactionDto;
import com.module.paymentsms.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/transaction")
@Slf4j
public class TransactionControllerImpl implements TransactionController{

    private final BuildResponse buildResponse;
    private final TransactionService transactionService;

    @Autowired
    public TransactionControllerImpl(
            BuildResponse buildResponse,
            TransactionService transactionService
    ) {
        this.buildResponse = buildResponse;
        this.transactionService = transactionService;
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<Object> getTransactionById(@PathVariable Long id) {
        try {
            log.info("Retrieving transaction by ID: {}", id);
            TransactionDto transaction = transactionService.getTransactionById(id);
            return buildResponse.success(transaction, "Transaction retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving transaction by ID {}: {}", id, e.getMessage(), e);
            return buildResponse.error("Failed to retrieve transaction: " + e.getMessage(), null, HttpStatus.NOT_FOUND);
        }
    }

    @Override
    @GetMapping("/ref/{ref}")
    public ResponseEntity<Object> getTransactionByRef(@PathVariable String ref) {
        try {
            log.info("Retrieving transaction by reference: {}", ref);
            TransactionDto transaction = transactionService.getTransactionByRef(ref);
            return buildResponse.success(transaction, "Transaction retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving transaction by ref {}: {}", ref, e.getMessage(), e);
            return buildResponse.error("Failed to retrieve transaction: " + e.getMessage(), null, HttpStatus.NOT_FOUND);
        }
    }

    @Override
    @GetMapping
    public ResponseEntity<Object> getAllTransactions(
            @RequestParam(required = false) Long walletId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime createdAtStartDate,
            @RequestParam(required = false) LocalDateTime createdAtEndDate,
            @RequestParam(required = false) LocalDateTime updatedAtStartDate,
            @RequestParam(required = false) LocalDateTime updatedAtEndDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        try {
            // Validate pagination parameters (1-based pagination)
            if (page < 1) {
                return buildResponse.error("Page number must be greater than or equal to 1", null, HttpStatus.BAD_REQUEST);
            }
            if (size < 1) {
                return buildResponse.error("Page size must be greater than or equal to 1", null, HttpStatus.BAD_REQUEST);
            }

            log.info("Retrieving transactions with filters - walletId: {}, provider: {}, status: {}, page: {}, size: {}", 
                    walletId, provider, status, page, size);
            
            PaginationDto<TransactionDto> transactions = transactionService.getAllTransactions(
                    walletId, provider, sender, method, type, status,
                    createdAtStartDate, createdAtEndDate,
                    updatedAtStartDate, updatedAtEndDate,
                    page, size
            );
            return buildResponse.success(transactions, "Transactions retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving transactions: {}", e.getMessage(), e);
            return buildResponse.error("Failed to retrieve transactions: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @GetMapping("/callback/{id}")
    public ResponseEntity<Object> getTransactionCallbackById(@PathVariable Long id) {
        try {
            log.info("Retrieving transaction callback by ID: {}", id);
            TransactionCallbackDto callback = transactionService.getTransactionCallbackById(id);
            return buildResponse.success(callback, "Transaction callback retrieved successfully");
        } catch (UnsupportedOperationException e) {
            log.warn("Method not implemented: {}", e.getMessage());
            return buildResponse.error(e.getMessage(), null, HttpStatus.NOT_IMPLEMENTED);
        } catch (Exception e) {
            log.error("Error retrieving transaction callback by ID {}: {}", id, e.getMessage(), e);
            return buildResponse.error("Failed to retrieve transaction callback: " + e.getMessage(), null, HttpStatus.NOT_FOUND);
        }
    }

    @Override
    @GetMapping("/callbacks")
    public ResponseEntity<Object> getAllTransactionCallbacks(
            @RequestParam(required = false) Long transactionId,
            @RequestParam(required = false) LocalDateTime createdAtStartDate,
            @RequestParam(required = false) LocalDateTime createdAtEndDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        try {
            // Validate pagination parameters (1-based pagination)
            if (page < 1) {
                return buildResponse.error("Page number must be greater than or equal to 1", null, HttpStatus.BAD_REQUEST);
            }
            if (size < 1) {
                return buildResponse.error("Page size must be greater than or equal to 1", null, HttpStatus.BAD_REQUEST);
            }

            log.info("Retrieving transaction callbacks - transactionId: {}, page: {}, size: {}", 
                    transactionId, page, size);
            
            PaginationDto<TransactionCallbackDto> callbacks = transactionService.getAllTransactionCallbacks(
                    transactionId, createdAtStartDate, createdAtEndDate, page, size
            );
            return buildResponse.success(callbacks, "Transaction callbacks retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving transaction callbacks: {}", e.getMessage(), e);
            return buildResponse.error("Failed to retrieve transaction callbacks: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
