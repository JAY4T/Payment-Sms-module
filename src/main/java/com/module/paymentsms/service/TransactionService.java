package com.module.paymentsms.service;

import com.module.paymentsms.dto.PaginationDto;
import com.module.paymentsms.dto.TransactionCallbackDto;
import com.module.paymentsms.dto.TransactionDto;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface TransactionService {
    TransactionDto getTransactionById(Long id);
    TransactionDto getTransactionByRef(String ref);
    PaginationDto<TransactionDto> getAllTransactions(Long walletId, String provider, String sender, String method, String type, String status, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Integer page, Integer size);
    TransactionCallbackDto getTransactionCallbackById(Long id);
    PaginationDto<TransactionCallbackDto> getAllTransactionCallbacks(Long transactionId, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, Integer page, Integer size);
}
