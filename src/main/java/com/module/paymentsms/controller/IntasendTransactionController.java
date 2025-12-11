package com.module.paymentsms.controller;

import com.module.paymentsms.dto.CheckoutCreationDto;
import com.module.paymentsms.dto.TransactionDto;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface IntasendTransactionController {
    ResponseEntity<Object> checkout(CheckoutCreationDto checkoutCreationDto) throws Exception;
    ResponseEntity<Object> getTransactionById(Long id);
    ResponseEntity<Object> getTransactionByRef(String transactionRef);
    ResponseEntity<Object> handleCallback(Map<String, Object> data);
}
