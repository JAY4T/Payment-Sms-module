package com.module.paymentsms.controller;

import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

public interface TransactionController {
    ResponseEntity<Object> getTransactionById(Long id);
    ResponseEntity<Object> getTransactionByRef(String ref);
    ResponseEntity<Object> getAllTransactions(Long walletId, String provider, String sender, String method, String type, String status, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Integer page, Integer size);
    ResponseEntity<Object> getTransactionCallbackById(Long id);
    ResponseEntity<Object> getAllTransactionCallbacks(Long transactionId, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, Integer page, Integer size);
}
