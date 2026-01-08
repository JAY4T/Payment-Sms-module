package com.module.paymentsms.controller;

import com.module.paymentsms.config.BuildResponse;
import com.module.paymentsms.dto.IntasendCheckoutCreationDto;
import com.module.paymentsms.dto.IntasendMpesaBTBPaybillDto;
import com.module.paymentsms.dto.TransactionDto;
import com.module.paymentsms.service.IntasendTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/intasend/transaction")
@Slf4j
public class IntasendTransactionControllerImpl implements IntasendTransactionController{
    private final BuildResponse buildResponse;
    private final IntasendTransactionService intasendTransactionService;

    @Autowired
    public IntasendTransactionControllerImpl(
            BuildResponse buildResponse,
            IntasendTransactionService intasendTransactionService
    ) {
        this.buildResponse = buildResponse;
        this.intasendTransactionService = intasendTransactionService;
    }

    @Override
    @PostMapping("/checkout")
    public ResponseEntity<Object> checkout(@RequestBody IntasendCheckoutCreationDto intasendCheckoutCreationDto) throws Exception {
        try {
            TransactionDto transaction = intasendTransactionService.checkout(intasendCheckoutCreationDto);
            return buildResponse.success(transaction, "Checkout initiated successfully", null, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Error during checkout: {}", e.getMessage(), e);
            return buildResponse.error("Checkout failed: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @PostMapping("/btb-paybill")
    public ResponseEntity<Object> btbPayBill(@RequestBody IntasendMpesaBTBPaybillDto intasendMpesaBTBPaybillDto) {
        try {
            TransactionDto transaction = intasendTransactionService.btbPayBill(intasendMpesaBTBPaybillDto);
            return buildResponse.success(transaction, "Pay bill checkout initiated successfully", null, HttpStatus.OK);
        } catch (Exception e) {
            return buildResponse.error("Checkout failed: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<Object> getTransactionById(@PathVariable Long id) {
        try {
            TransactionDto transaction = intasendTransactionService.getTransactionById(id);
            if (transaction == null) {
                return buildResponse.error("Transaction not found", null, HttpStatus.NOT_FOUND);
            }
            return buildResponse.success(transaction, "Transaction retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving transaction by ID {}: {}", id, e.getMessage(), e);
            return buildResponse.error("Failed to retrieve transaction: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @GetMapping("/ref/{transactionRef}")
    public ResponseEntity<Object> getTransactionByRef(@PathVariable String transactionRef) {
        try {
            TransactionDto transaction = intasendTransactionService.getTransactionByRef(transactionRef);
            if (transaction == null) {
                return buildResponse.error("Transaction not found", null, HttpStatus.NOT_FOUND);
            }
            return buildResponse.success(transaction, "Transaction retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving transaction by ref {}: {}", transactionRef, e.getMessage(), e);
            return buildResponse.error("Failed to retrieve transaction: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @PostMapping("/collection-callback")
    public ResponseEntity<Object> handleCollectionCallback(@RequestBody Map<String, Object> data) {
        try {
            log.info("Received transaction callback: {}", data);
            TransactionDto transaction = intasendTransactionService.handleCollectionCallback(data);
            if (transaction == null) {
                return buildResponse.error("Failed to process callback", null, HttpStatus.OK);
            }
            return buildResponse.success(transaction, "Callback processed successfully");
        } catch (Exception e) {
            log.error("Error processing callback: {}", e.getMessage(), e);
            return buildResponse.error("Callback processing failed: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
