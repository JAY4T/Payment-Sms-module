package com.module.paymentsms.controller;

import com.module.paymentsms.dto.IntasendBankPayoutDto;
import com.module.paymentsms.dto.IntasendCheckoutCreationDto;
import com.module.paymentsms.dto.IntasendMpesaBTBPaybillDto;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface IntasendTransactionController {
    ResponseEntity<Object> checkout(IntasendCheckoutCreationDto intasendCheckoutCreationDto) throws Exception;
    ResponseEntity<Object> btbPayBill(IntasendMpesaBTBPaybillDto intasendMpesaBTBPaybillDto);
    ResponseEntity<Object> btbBankPayout(IntasendBankPayoutDto intasendBankPayoutDto);
    ResponseEntity<Object> getTransactionById(Long id);
    ResponseEntity<Object> getTransactionByRef(String transactionRef);
    ResponseEntity<Object> reconcileCollectionTransaction(Long id);
    ResponseEntity<Object> reconcileSendMoneyTransaction(Long id);
    ResponseEntity<Object> handleCallback(Map<String, Object> data);
    ResponseEntity<Object> handleSendMoneyCallback(Map<String, Object> data);
}
