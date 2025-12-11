package com.module.paymentsms.service;

import com.module.paymentsms.dto.CheckoutCreationDto;
import com.module.paymentsms.dto.TransactionDto;

import java.util.Map;

public interface IntasendTransactionService {
    TransactionDto checkout(CheckoutCreationDto checkoutCreationDto) throws Exception;
    TransactionDto getTransactionById(Long id);
    TransactionDto getTransactionByRef(String transactionRef);
    TransactionDto handleCallback(Map<String, Object> data);
}
