package com.module.paymentsms.service;

import com.module.paymentsms.dto.*;

import java.util.List;
import java.util.Map;

public interface IntasendTransactionService {
    TransactionDto checkout(IntasendCheckoutCreationDto intasendCheckoutCreationDto) throws Exception;
    TransactionDto btcMpesa(IntasendMpesaBTCDto intasendMpesaBTCDto);
    TransactionDto btbPayBill(IntasendMpesaBTBPaybillDto intasendMpesaBTBPaybillDto) throws Exception;
    TransactionDto btbTillNumber(IntasendMpesaBTBTillDto intasendMpesaBTBTillDto);
    TransactionDto btbBankPayout(IntasendBankPayoutDto intasendBankPayoutDto);

    TransactionDto approveSendMoneyTransaction(String transactionTrackingId);
    TransactionDto getTransactionById(Long id);
    TransactionDto getTransactionByRef(String transactionRef);
    TransactionDto handleCallback(Map<String, Object> data);
    TransactionDto handleCollectionCallback(Map<String, Object> data);
    TransactionDto handleSendMoneyCallback(Map<String, Object> data);
    TransactionDto handleReversalCallback(Map<String, Object> data);
    TransactionDto handleWalletTransferCallback(Map<String, Object> data);
    void updateTransactionStatusFromPolling(Long transactionId, String status, String charges, String provider, 
                                           String account, String currency, String invoiceId, String failureReason);
    void updateSendMoneyBatchFromPolling(Long batchTransactionId, String newBatchStatus, 
                                        String batchStatusCode, String batchStatusDescription,
                                        List<Map<String, Object>> transactions);
}
