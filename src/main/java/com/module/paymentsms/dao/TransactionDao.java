package com.module.paymentsms.dao;

import com.module.paymentsms.entity.Transaction;
import com.module.paymentsms.entity.TransactionCallback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface TransactionDao {
    Transaction createTransaction(Transaction transaction);
    Transaction updateTransaction(Transaction transaction);
    Transaction getTransactionById(Long id);
    Transaction getTransactionByReference(String reference);
    Page<Transaction> getAllTransactions(Long walletId, String provider, String sender, String method, String type, String status, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Pageable pageable); // filter with 1 based pagination

    TransactionCallback createTransactionCallback(TransactionCallback transactionCallback);
    Page<TransactionCallback> getAllTransactionCallbacks(Long transactionId, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, Pageable pageable);
}
