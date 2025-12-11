package com.module.paymentsms.service;

import com.module.paymentsms.dao.TransactionDao;
import com.module.paymentsms.dto.PaginationDto;
import com.module.paymentsms.dto.TransactionCallbackDto;
import com.module.paymentsms.dto.TransactionDto;
import com.module.paymentsms.entity.Transaction;
import com.module.paymentsms.entity.TransactionCallback;
import com.module.paymentsms.mapper.TransactionDtoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TransactionServiceImpl implements TransactionService{

    private final TransactionDao transactionDao;
    private final TransactionDtoMapper transactionDtoMapper;

    @Autowired
    public TransactionServiceImpl(
            TransactionDao transactionDao,
            TransactionDtoMapper transactionDtoMapper
    ) {
        this.transactionDao = transactionDao;
        this.transactionDtoMapper = transactionDtoMapper;
    }

    @Override
    public TransactionDto getTransactionById(Long id) {
        Transaction transaction = transactionDao.getTransactionById(id);
        if (transaction == null) {
            throw new RuntimeException("Transaction not found with ID: " + id);
        }
        return transactionDtoMapper.toTransactionDto(transaction);
    }

    @Override
    public TransactionDto getTransactionByRef(String ref) {
        Transaction transaction = transactionDao.getTransactionByReference(ref);
        if (transaction == null) {
            throw new RuntimeException("Transaction not found with reference: " + ref);
        }
        return transactionDtoMapper.toTransactionDto(transaction);
    }

    @Override
    public PaginationDto<TransactionDto> getAllTransactions(Long walletId, String provider, String sender, String method, String type, String status, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Integer page, Integer size) {
        try {
            // Create Pageable object
            Pageable pageable = PageRequest.of(page, size);

            // Get paginated transactions from DAO
            Page<Transaction> transactionPage = transactionDao.getAllTransactions(walletId, provider, sender, method, type, status, createdAtStartDate, createdAtEndDate, updatedAtStartDate, updatedAtEndDate, pageable);

            // Convert to DTOs
            List<TransactionDto> transactionDtos = transactionPage.getContent()
                    .stream()
                    .map(transactionDtoMapper::toTransactionDto)
                    .collect(Collectors.toList());

            // Create pagination response
            return new PaginationDto<>(
                    transactionDtos,
                    page,
                    size,
                    transactionPage.getTotalElements()
            );

        } catch (Exception e) {
            log.error("Failed to get transactions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve transactions", e);
        }
    }

    @Override
    public TransactionCallbackDto getTransactionCallbackById(Long id) {
        // Note: TransactionDao doesn't have a method to get callback by ID
        // This would need to be added to the DAO if needed
        throw new UnsupportedOperationException("Method not implemented - TransactionDao missing getTransactionCallbackById method");
    }

    @Override
    public PaginationDto<TransactionCallbackDto> getAllTransactionCallbacks(Long transactionId, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, Integer page, Integer size) {
        try {
            // Create Pageable object
            Pageable pageable = PageRequest.of(page, size);

            // Get paginated transaction callbacks from DAO
            Page<TransactionCallback> callbackPage = transactionDao.getAllTransactionCallbacks(transactionId, createdAtStartDate, createdAtEndDate, pageable);

            // Convert to DTOs
            List<TransactionCallbackDto> callbackDtos = callbackPage.getContent()
                    .stream()
                    .map(transactionDtoMapper::toTransactionCallbackDto)
                    .collect(Collectors.toList());

            // Create pagination response
            return new PaginationDto<>(
                    callbackDtos,
                    page,
                    size,
                    callbackPage.getTotalElements()
            );

        } catch (Exception e) {
            log.error("Failed to get transaction callbacks: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve transaction callbacks", e);
        }
    }
}
