package com.module.paymentsms.service;

import com.google.gson.Gson;
import com.module.paymentsms.dao.TransactionDao;
import com.module.paymentsms.dao.WalletDao;
import com.module.paymentsms.dto.CheckoutCreationDto;
import com.module.paymentsms.dto.TransactionDto;
import com.module.paymentsms.entity.Transaction;
import com.module.paymentsms.entity.TransactionCallback;
import com.module.paymentsms.entity.Wallet;
import com.module.paymentsms.mapper.TransactionDtoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class IntasendTransactionServiceImpl implements IntasendTransactionService {

    private final TransactionDao transactionDao;
    private final WalletDao walletDao;
    private final TransactionDtoMapper transactionDtoMapper;

    @Value("${intasend.mpesa.checkout.url}")
    private String mpesaCheckoutUrl;

    @Value("${intasend.payment.status.url}")
    private String paymentStatusUrl;

    @Value("${intasend.secret.key}")
    private String intasendSecretKey;


    @Autowired
    public IntasendTransactionServiceImpl(
            TransactionDao transactionDao,
            WalletDao walletDao,
            TransactionDtoMapper transactionDtoMapper
    ) {
        this.transactionDao = transactionDao;
        this.walletDao = walletDao;
        this.transactionDtoMapper = transactionDtoMapper;
    }

    @Override
    @Transactional
    public TransactionDto checkout(CheckoutCreationDto checkoutCreationDto) throws Exception {

        LocalDateTime now = LocalDateTime.now();

        String transactionRef = System.currentTimeMillis() + "_" + UUID.randomUUID() + "_MAG";

        Wallet wallet = walletDao.getWalletById(checkoutCreationDto.getWalletId());

        Transaction transaction = Transaction.builder()
                .transactionRef(transactionRef)
                .sender(checkoutCreationDto.getPhoneNumber())
                .method("MOBILE_WALLET")
                .type("CREDIT")
                .currency(checkoutCreationDto.getCurrency())
                .amount(checkoutCreationDto.getAmount())
                .status("PENDING")
                .narration(checkoutCreationDto.getNarration())
                .createdAt(now)
                .updatedAt(now)
                .callbacks(new ArrayList<>())
                .wallet(wallet)
                .build();

        transactionDao.createTransaction(transaction);

        try {
            intasendMpesaCheckout(transaction);
            
            // Start async polling as fallback in case callback fails
            pollTransactionStatusAsync(transaction.getId());
            
            // Return immediately with PENDING status
            // Status will be updated via callback webhook or fallback polling
            return transactionDtoMapper.toTransactionDto(transaction);

        } catch (Exception e) {
            transaction.setStatus("FAILED");
            transaction.setFailureReason(e.getMessage());
            transaction.setUpdatedAt(LocalDateTime.now());
            transactionDao.updateTransaction(transaction);
            throw new RuntimeException(e);
        }
    }
    
    @Async("taskExecutor")
    public void pollTransactionStatusAsync(Long transactionId) {
        log.info("Starting async API polling (fallback) for transaction {}", transactionId);
        
        try {
            // Initial delay to give callback a chance to arrive first
            Thread.sleep(10000); // 10 seconds initial delay
            
            int maxAttempts = 30; // Poll for up to 1 minute after initial delay
            int attempts = 0;
            
            while (attempts < maxAttempts) {
                Transaction transaction = transactionDao.getTransactionById(transactionId);
                
                if (transaction == null) {
                    log.error("Transaction {} not found during async polling", transactionId);
                    break;
                }
                
                String status = transaction.getStatus();
                
                // Exit if transaction reached terminal state (likely via callback)
                if (status.equals("COMPLETED") || status.equals("FAILED")) {
                    log.info("Transaction {} already settled via callback: {}", transactionId, status);
                    break;
                }
                
                // Poll Intasend API to check transaction status as fallback
                log.debug("Fallback polling Intasend API for transaction {}", transactionId);
                pollIntasendTransactionStatus(transaction);
                
                attempts++;
                Thread.sleep(2000);
            }
            
            if (attempts >= maxAttempts) {
                log.warn("Async polling timed out for transaction {} - may need manual verification", transactionId);
            }
            
        } catch (InterruptedException e) {
            log.error("Transaction async polling interrupted for transaction {}", transactionId, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error in async polling for transaction {}", transactionId, e);
        }
    }

    @Override
    public TransactionDto getTransactionById(Long id) {
        Transaction transaction = transactionDao.getTransactionById(id);
        return transaction != null ? transactionDtoMapper.toTransactionDto(transaction) : null;
    }

    @Override
    public TransactionDto getTransactionByRef(String transactionRef) {
        Transaction transaction = transactionDao.getTransactionByReference(transactionRef);
        return transaction != null ? transactionDtoMapper.toTransactionDto(transaction) : null;
    }

    @Override
    @Transactional
    public TransactionDto handleCallback(Map<String, Object> data) {
        try {
            String apiRef = data.get("api_ref").toString();
            Transaction transaction = transactionDao.getTransactionByReference(apiRef);
            
            if (transaction == null) {
                log.error("Transaction not found for api_ref: {}", apiRef);
                return null;
            }

            LocalDateTime now = LocalDateTime.now();

            // Convert callback data to JSON string
            Gson gson = new Gson();
            String jsonBody = gson.toJson(data);

            TransactionCallback transactionCallback = TransactionCallback.builder()
                    .transaction(transaction)
                    .body(jsonBody)
                    .createdAt(now)
                    .build();

            transactionDao.createTransactionCallback(transactionCallback);
            
            // Extract callback data
            String state = data.get("state").toString().toLowerCase();
            String charges = data.get("charges").toString();
            String provider = data.get("provider").toString();
            String account = data.get("account").toString();
            String currency = data.get("currency").toString();
            String invoiceId = data.get("invoice_id").toString();
            
            // Update transaction fields
            transaction.setProvider(provider);
            transaction.setSender(account);
            transaction.setCurrency(currency);
            transaction.setInvoiceId(invoiceId);
            transaction.setUpdatedAt(now);
            
            // Update status based on state
            switch (state) {
                case "complete":
                    transaction.setStatus("COMPLETED");
                    transaction.setFee(new BigDecimal(charges));
                    break;
                    
                case "cancelled":
                case "failed":
                    transaction.setStatus("FAILED");
                    if (data.containsKey("failed_reason")) {
                        transaction.setFailureReason(data.get("failed_reason").toString());
                    }
                    break;
                    
                case "processing":
                    transaction.setStatus("PROCESSING");
                    break;
                    
                default:
                    log.warn("Unknown transaction state: {} for transaction {}", state, apiRef);
            }
            
            transactionDao.updateTransaction(transaction);
            
            log.info("Callback processed for transaction {}: status={}", apiRef, transaction.getStatus());
            
            return transactionDtoMapper.toTransactionDto(transaction);
            
        } catch (Exception e) {
            log.error("Error processing callback", e);
            return null;
        }
    }

    private Transaction intasendMpesaCheckout(Transaction transaction) throws Exception {

        Map<String, Object> requestBody = Map.of(
                "amount", String.valueOf(transaction.getAmount()),
                "phone_number", transaction.getSender(),
                "wallet_id", transaction.getWallet().getIntasendWalletId(),
                "api_ref", transaction.getTransactionRef()
        );

        Gson gson = new Gson();
        String jsonBody = gson.toJson(requestBody);

        System.out.println("Intasend payment request: "+requestBody);

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(new URI(mpesaCheckoutUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + intasendSecretKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> postResponse = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());

        log.debug("Intasend postResponse {}: ", postResponse);
        System.out.println("Intasend postResponse: "+ postResponse);

        if (postResponse.statusCode() != HttpStatus.OK.value()) {
            log.error("Failed to initialize payment::: Status code {} response body:: {}", postResponse.statusCode(), postResponse.body());

            throw new Exception("Failed to initialize payment::: Status code " + postResponse.statusCode() +" response body:: "+ postResponse.body());
        }

        String jsonString = postResponse.body();
        Map<String, Object> responseMap = gson.fromJson(jsonString, Map.class);

        log.debug("Intasend responseMap {}: ", responseMap);
        System.out.println("Intasend responseMap: "+ responseMap);

        Map<String, Object> invoice = (Map<String, Object>) responseMap.get("invoice");
        String invoiceId = invoice.get("invoice_id").toString();

        transaction.setInvoiceId(invoiceId);

        transactionDao.updateTransaction(transaction);

        return transaction;

    }
    
    private void pollIntasendTransactionStatus(Transaction transaction) {
        log.debug("Polling Intasend API for transaction {}", transaction.getTransactionRef());
        
        try {
            if (transaction.getInvoiceId() == null) {
                log.warn("Cannot poll Intasend - no invoice ID for transaction {}", transaction.getTransactionRef());
                return;
            }
            
            // Build request body
            Map<String, String> requestBody = Map.of("invoice_id", transaction.getInvoiceId());
            
            Gson gson = new Gson();
            String jsonBody = gson.toJson(requestBody);
            
            // Make HTTP POST request to Intasend payment status endpoint
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(paymentStatusUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + intasendSecretKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.debug("Intasend polling response status: {}", response.statusCode());
            
            if (response.statusCode() == HttpStatus.OK.value()) {
                Map<String, Object> responseData = gson.fromJson(response.body(), Map.class);
                
                // Extract invoice data from response
                Map<String, Object> invoice = (Map<String, Object>) responseData.get("invoice");
                
                if (invoice != null) {
                    String state = invoice.get("state").toString().toLowerCase();
                    
                    // Map API state to our database status format
                    String newStatus = mapStateToStatus(state);
                    
                    // Fetch latest transaction status from database to check if callback already updated it
                    Transaction latestTransaction = transactionDao.getTransactionById(transaction.getId());
                    String currentStatus = latestTransaction != null ? latestTransaction.getStatus() : null;
                    
                    // Only update if status has changed (avoid duplicity)
                    if (newStatus != null && !newStatus.equals(currentStatus)) {
                        String charges = invoice.get("charges").toString();
                        String provider = invoice.get("provider").toString();
                        String account = invoice.get("account").toString();
                        String currency = invoice.get("currency").toString();
                        String invoiceId = invoice.get("invoice_id").toString();
                        
                        LocalDateTime now = LocalDateTime.now();
                        
                        // Update transaction fields
                        transaction.setProvider(provider);
                        transaction.setSender(account);
                        transaction.setCurrency(currency);
                        transaction.setInvoiceId(invoiceId);
                        transaction.setUpdatedAt(now);
                        transaction.setStatus(newStatus);
                        
                        // Set additional fields based on state
                        if (newStatus.equals("COMPLETED")) {
                            transaction.setFee(new BigDecimal(charges));
                        } else if (newStatus.equals("FAILED")) {
                            if (invoice.containsKey("failed_reason") && invoice.get("failed_reason") != null) {
                                transaction.setFailureReason(invoice.get("failed_reason").toString());
                            }
                        }
                        
                        updateTransactionInNewTransaction(transaction);
                        
                        log.info("Polled and updated transaction {}: {} -> {}", 
                                transaction.getTransactionRef(), currentStatus, newStatus);
                    } else {
                        log.debug("Transaction {} status unchanged: {}", transaction.getTransactionRef(), currentStatus);
                    }
                }
            } else {
                log.error("Failed to poll Intasend - Status: {}, Body: {}", response.statusCode(), response.body());
            }
            
        } catch (Exception e) {
            log.error("Error polling Intasend for transaction {}", transaction.getTransactionRef(), e);
        }
    }
    
    @Transactional
    protected void updateTransactionInNewTransaction(Transaction transaction) {
        transactionDao.updateTransaction(transaction);
    }
    
    private String mapStateToStatus(String state) {
        switch (state) {
            case "complete":
                return "COMPLETED";
            case "cancelled":
            case "failed":
                return "FAILED";
            case "processing":
                return "PROCESSING";
            default:
                log.warn("Unknown transaction state: {}", state);
                return null;
        }
    }
}
