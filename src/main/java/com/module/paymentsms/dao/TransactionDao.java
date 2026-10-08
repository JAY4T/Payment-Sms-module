package com.module.paymentsms.dao;

import com.module.paymentsms.entity.Transaction;
import com.module.paymentsms.entity.TransactionCallback;
import com.module.paymentsms.entity.TransactionMetaData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionDao {
    Transaction createTransaction(Transaction transaction);
    // Persists and flushes immediately, so a UNIQUE violation (e.g. two concurrent wallet
    // transfers sharing an idempotency key) is raised before the caller reaches the payment
    // provider rather than at commit time. See IntasendWalletServiceImpl.transferBetweenWallets.
    Transaction createTransactionAndFlush(Transaction transaction);
    Transaction updateTransaction(Transaction transaction);
    Transaction getTransactionById(Long id);
    Transaction getTransactionByReference(String reference);
    Transaction getTransactionByIntasendTrackingId(String trackingId);
    Page<Transaction> getAllTransactions(Long walletId, String provider, String sender, String method, String type, String status, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Pageable pageable);
    // All three sweeps are bounded (limit) and ordered oldest-first so a backlog drains in
    // arrival order instead of loading every pending row into memory on each run.
    List<Transaction> getPendingSendMoneyBatches(int limit);
    List<Transaction> getPendingCollectionTransactions(int limit);
    List<Transaction> getCollectionTransactionsPendingClearingStatus(int limit);

    TransactionCallback createTransactionCallback(TransactionCallback transactionCallback);
    Page<TransactionCallback> getAllTransactionCallbacks(Long transactionId, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, Pageable pageable);
    
    TransactionMetaData createTransactionMetaData(TransactionMetaData transactionMetaData);
}
