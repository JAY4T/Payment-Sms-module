package com.module.paymentsms.service;

import com.google.gson.Gson;
import com.module.paymentsms.config.RabbitConfig;
import com.module.paymentsms.dao.TransactionDao;
import com.module.paymentsms.dao.WalletDao;
import com.module.paymentsms.dto.*;
import com.module.paymentsms.entity.*;
import com.module.paymentsms.mapper.TransactionDtoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class IntasendTransactionServiceImpl implements IntasendTransactionService {

    private final TransactionDao transactionDao;
    private final WalletDao walletDao;

    private final IntasendWalletService intasendWalletService;
    private final TransactionDtoMapper transactionDtoMapper;
    private final ApplicationContext applicationContext;
    private final TransactionTemplate transactionTemplate;
    private final RabbitTemplate rabbitTemplate;

    private final ConcurrentHashMap<Long, LocalDateTime> pendingTransactions = new ConcurrentHashMap<>();

    @Value("${intasend.mpesa.checkout.url}")
    private String mpesaCheckoutUrl;

    @Value("${intasend.checkout.url}")
    private String checkoutUrl;

    @Value("${intasend.payment.status.url}")
    private String paymentStatusUrl;
    
    @Value("${intasend.sendmoney.url}")
    private String sendMoneyUrl;

    @Value("${intasend.sendmoney.approval.url}")
    private String sendMoneyApprovalUrl;

    @Value("${intasend.secret.key}")
    private String intasendSecretKey;

    @Value("${intasend.public.key}")
    private String intasendPublicKey;

    @Value("${intasend.sendmoney.status.url}")
    private String intasendSendMoneyStatusUrl;


    @Autowired
    public IntasendTransactionServiceImpl(
            TransactionDao transactionDao,
            WalletDao walletDao,
            IntasendWalletService intasendWalletService,
            TransactionDtoMapper transactionDtoMapper,
            ApplicationContext applicationContext,
            PlatformTransactionManager transactionManager,
            RabbitTemplate rabbitTemplate
    ) {
        this.transactionDao = transactionDao;
        this.walletDao = walletDao;
        this.intasendWalletService = intasendWalletService;
        this.transactionDtoMapper = transactionDtoMapper;
        this.applicationContext = applicationContext;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public TransactionDto checkout(IntasendCheckoutCreationDto intasendCheckoutCreationDto) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String transactionRef = System.currentTimeMillis() + "_" + UUID.randomUUID() + "_MAG";

        TransactionMethod method = intasendCheckoutCreationDto.getMethod();

        Wallet wallet = walletDao.getWalletById(intasendCheckoutCreationDto.getWalletId());

        Transaction transaction = Transaction.builder()
                .transactionRef(transactionRef)
                .sender(intasendCheckoutCreationDto.getPhoneNumber())
                .method(method)
                .type("CREDIT")
                .currency(intasendCheckoutCreationDto.getCurrency())
                .amount(intasendCheckoutCreationDto.getAmount())
                .status("PENDING")
                .narration(intasendCheckoutCreationDto.getNotes())
                .createdAt(now)
                .updatedAt(now)
                .callbacks(new ArrayList<>())
                .wallet(wallet)
                .build();

        // Committed on its own so the PENDING record survives even if the Intasend call below
        // fails - it must not share a transaction with the failure-handling update, otherwise
        // rethrowing after that update rolls back both of them together.
        transactionTemplate.executeWithoutResult(status -> transactionDao.createTransaction(transaction));

        try {
            intasendCheckout(transaction, intasendCheckoutCreationDto.getRedirectUrl());

            pendingTransactions.put(transaction.getId(), now);

            return transactionDtoMapper.toTransactionDto(transaction);

        } catch (Exception e) {
            transaction.setStatus("FAILED");
            transaction.setFailureReason(e.getMessage());
            transaction.setUpdatedAt(LocalDateTime.now());
            transactionTemplate.executeWithoutResult(status -> transactionDao.updateTransaction(transaction));
            throw new RuntimeException(e);
        }
    }

    @Override
    public TransactionDto btcMpesa(IntasendMpesaBTCDto intasendMpesaBTCDto) {
        LocalDateTime now = LocalDateTime.now();
        String batchRef = System.currentTimeMillis() + "_" + UUID.randomUUID() + "_MAG_BATCH";

        Wallet wallet = walletDao.getWalletById(intasendMpesaBTCDto.getWalletId());

        BigDecimal totalAmount = intasendMpesaBTCDto.getTransactions().stream()
                .map(IntasendMpesaBTCDto.MpesaBTCTransactionDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String recipients = intasendMpesaBTCDto.getTransactions().stream()
                .map(IntasendMpesaBTCDto.MpesaBTCTransactionDto::getRecipientPhoneNumber)
                .reduce((a, b) -> a + ", " + b)
                .orElse("N/A");

        Transaction batchTransaction = Transaction.builder()
                .transactionRef(batchRef)
                .sender(wallet.getName())
                .method(TransactionMethod.INTASEND_B_T_C_MPESA)
                .type("DEBIT")
                .currency(intasendMpesaBTCDto.getCurrency())
                .amount(totalAmount)
                .status("PENDING")
                .narration("B2C Batch: " + intasendMpesaBTCDto.getTransactions().size() + " transaction(s) to " + recipients)
                .hasBatch(true)
                .createdAt(now)
                .updatedAt(now)
                .callbacks(new ArrayList<>())
                .transactionMetaData(new ArrayList<>())
                .batchTransactions(new ArrayList<>())
                .wallet(wallet)
                .build();

        List<Transaction> childTransactions = new ArrayList<>();
        for (IntasendMpesaBTCDto.MpesaBTCTransactionDto txnDto : intasendMpesaBTCDto.getTransactions()) {
            String childRef = batchRef + "_CHILD_" + UUID.randomUUID();

            Transaction childTransaction = Transaction.builder()
                    .transactionRef(childRef)
                    .sender(txnDto.getRecipientPhoneNumber())
                    .method(TransactionMethod.INTASEND_B_T_C_MPESA)
                    .type("DEBIT")
                    .currency(intasendMpesaBTCDto.getCurrency())
                    .amount(txnDto.getAmount())
                    .status("PENDING")
                    .narration(txnDto.getNarration())
                    .hasBatch(false)
                    .parentTransaction(batchTransaction)
                    .createdAt(now)
                    .updatedAt(now)
                    .callbacks(new ArrayList<>())
                    .transactionMetaData(new ArrayList<>())
                    .wallet(wallet)
                    .build();

            childTransactions.add(childTransaction);
        }

        batchTransaction.setBatchTransactions(childTransactions);

        // Batch parent + all children committed together, independently of the failure-handling
        // update below, so they survive even if the Intasend call fails.
        transactionTemplate.executeWithoutResult(status -> {
            transactionDao.createTransaction(batchTransaction);
            for (Transaction child : childTransactions) {
                transactionDao.createTransaction(child);
            }
        });

        try {
            intasendB2CMpesa(batchTransaction, intasendMpesaBTCDto);

            pendingTransactions.put(batchTransaction.getId(), now);

            return transactionDtoMapper.toTransactionDto(batchTransaction);

        } catch (Exception e) {
            batchTransaction.setStatus("FAILED");
            batchTransaction.setFailureReason(e.getMessage());
            batchTransaction.setUpdatedAt(LocalDateTime.now());

            for (Transaction child : childTransactions) {
                child.setStatus("FAILED");
                child.setFailureReason("Batch initiation failed: " + e.getMessage());
                child.setUpdatedAt(LocalDateTime.now());
            }

            transactionTemplate.executeWithoutResult(status -> {
                transactionDao.updateTransaction(batchTransaction);
                for (Transaction child : childTransactions) {
                    transactionDao.updateTransaction(child);
                }
            });

            throw new RuntimeException(e);
        }
    }

    @Override
    public TransactionDto btbPayBill(IntasendMpesaBTBPaybillDto intasendMpesaBTBPaybillDto) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String batchRef = System.currentTimeMillis() + "_" + UUID.randomUUID() + "_MAG_B2B_BATCH";

        Wallet wallet = walletDao.getWalletById(intasendMpesaBTBPaybillDto.getWalletId());

        BigDecimal totalAmount = intasendMpesaBTBPaybillDto.getTransactions().stream()
                .map(IntasendMpesaBTBPaybillDto.MpesaBTBPaybillTransactionDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String recipients = intasendMpesaBTBPaybillDto.getTransactions().stream()
                .map(txn -> txn.getPaybillNumber() + " (Acc: " + txn.getAccountReference() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("N/A");

        Transaction batchTransaction = Transaction.builder()
                .transactionRef(batchRef)
                .sender(wallet.getName())
                .method(TransactionMethod.INTASEND_B_T_B_MPESA_PAYBILL)
                .type("DEBIT")
                .currency(intasendMpesaBTBPaybillDto.getCurrency())
                .amount(totalAmount)
                .status("PENDING")
                .narration("B2B PayBill Batch: " + intasendMpesaBTBPaybillDto.getTransactions().size() + " transaction(s) to " + recipients)
                .hasBatch(true)
                .createdAt(now)
                .updatedAt(now)
                .callbacks(new ArrayList<>())
                .transactionMetaData(new ArrayList<>())
                .batchTransactions(new ArrayList<>())
                .wallet(wallet)
                .build();

        List<Transaction> childTransactions = new ArrayList<>();
        for (IntasendMpesaBTBPaybillDto.MpesaBTBPaybillTransactionDto txnDto : intasendMpesaBTBPaybillDto.getTransactions()) {
            String childRef = batchRef + "_CHILD_" + UUID.randomUUID();

            Transaction childTransaction = Transaction.builder()
                    .transactionRef(childRef)
                    .sender(txnDto.getPaybillNumber() + " - " + txnDto.getAccountReference())
                    .method(TransactionMethod.INTASEND_B_T_B_MPESA_PAYBILL)
                    .type("DEBIT")
                    .currency(intasendMpesaBTBPaybillDto.getCurrency())
                    .amount(txnDto.getAmount())
                    .status("PENDING")
                    .narration(txnDto.getNarration())
                    .hasBatch(false)
                    .parentTransaction(batchTransaction)
                    .createdAt(now)
                    .updatedAt(now)
                    .callbacks(new ArrayList<>())
                    .transactionMetaData(new ArrayList<>())
                    .wallet(wallet)
                    .build();

            childTransactions.add(childTransaction);
        }

        batchTransaction.setBatchTransactions(childTransactions);

        // Batch parent + all children committed together, independently of the failure-handling
        // update below, so they survive even if the Intasend call fails.
        transactionTemplate.executeWithoutResult(status -> {
            transactionDao.createTransaction(batchTransaction);
            for (Transaction child : childTransactions) {
                transactionDao.createTransaction(child);
            }
        });

        try {
            intasendB2BPayBill(batchTransaction, intasendMpesaBTBPaybillDto);

            pendingTransactions.put(batchTransaction.getId(), now);

            return transactionDtoMapper.toTransactionDto(batchTransaction);

        } catch (Exception e) {
            batchTransaction.setStatus("FAILED");
            batchTransaction.setFailureReason(e.getMessage());
            batchTransaction.setUpdatedAt(LocalDateTime.now());

            for (Transaction child : childTransactions) {
                child.setStatus("FAILED");
                child.setFailureReason("B2B PayBill batch initiation failed: " + e.getMessage());
                child.setUpdatedAt(LocalDateTime.now());
            }

            transactionTemplate.executeWithoutResult(status -> {
                transactionDao.updateTransaction(batchTransaction);
                for (Transaction child : childTransactions) {
                    transactionDao.updateTransaction(child);
                }
            });

            throw new RuntimeException(e);
        }
    }

    @Override
    public TransactionDto btbTillNumber(IntasendMpesaBTBTillDto intasendMpesaBTBTillDto) {
        LocalDateTime now = LocalDateTime.now();
        String batchRef = System.currentTimeMillis() + "_" + UUID.randomUUID() + "_MAG_B2B_TILL_BATCH";

        Wallet wallet = walletDao.getWalletById(intasendMpesaBTBTillDto.getWalletId());

        BigDecimal totalAmount = intasendMpesaBTBTillDto.getTransactions().stream()
                .map(IntasendMpesaBTBTillDto.MpesaBTBTillTransactionDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String recipients = intasendMpesaBTBTillDto.getTransactions().stream()
                .map(IntasendMpesaBTBTillDto.MpesaBTBTillTransactionDto::getTillNumber)
                .reduce((a, b) -> a + ", " + b)
                .orElse("N/A");

        Transaction batchTransaction = Transaction.builder()
                .transactionRef(batchRef)
                .sender(wallet.getName())
                .method(TransactionMethod.INTASEND_B_T_B_MPESA_TILL)
                .type("DEBIT")
                .currency(intasendMpesaBTBTillDto.getCurrency())
                .amount(totalAmount)
                .status("PENDING")
                .narration("B2B Till Batch: " + intasendMpesaBTBTillDto.getTransactions().size() + " transaction(s) to Till " + recipients)
                .hasBatch(true)
                .createdAt(now)
                .updatedAt(now)
                .callbacks(new ArrayList<>())
                .transactionMetaData(new ArrayList<>())
                .batchTransactions(new ArrayList<>())
                .wallet(wallet)
                .build();

        List<Transaction> childTransactions = new ArrayList<>();
        for (IntasendMpesaBTBTillDto.MpesaBTBTillTransactionDto txnDto : intasendMpesaBTBTillDto.getTransactions()) {
            String childRef = batchRef + "_CHILD_" + UUID.randomUUID();

            Transaction childTransaction = Transaction.builder()
                    .transactionRef(childRef)
                    .sender("Till " + txnDto.getTillNumber())
                    .method(TransactionMethod.INTASEND_B_T_B_MPESA_TILL)
                    .type("DEBIT")
                    .currency(intasendMpesaBTBTillDto.getCurrency())
                    .amount(txnDto.getAmount())
                    .status("PENDING")
                    .narration(txnDto.getNarration())
                    .hasBatch(false)
                    .parentTransaction(batchTransaction)
                    .createdAt(now)
                    .updatedAt(now)
                    .callbacks(new ArrayList<>())
                    .transactionMetaData(new ArrayList<>())
                    .wallet(wallet)
                    .build();

            childTransactions.add(childTransaction);
        }

        batchTransaction.setBatchTransactions(childTransactions);

        // Batch parent + all children committed together, independently of the failure-handling
        // update below, so they survive even if the Intasend call fails.
        transactionTemplate.executeWithoutResult(status -> {
            transactionDao.createTransaction(batchTransaction);
            for (Transaction child : childTransactions) {
                transactionDao.createTransaction(child);
            }
        });

        try {
            intasendB2BTill(batchTransaction, intasendMpesaBTBTillDto);

            pendingTransactions.put(batchTransaction.getId(), now);

            return transactionDtoMapper.toTransactionDto(batchTransaction);

        } catch (Exception e) {
            batchTransaction.setStatus("FAILED");
            batchTransaction.setFailureReason(e.getMessage());
            batchTransaction.setUpdatedAt(LocalDateTime.now());

            for (Transaction child : childTransactions) {
                child.setStatus("FAILED");
                child.setFailureReason("B2B Till batch initiation failed: " + e.getMessage());
                child.setUpdatedAt(LocalDateTime.now());
            }

            transactionTemplate.executeWithoutResult(status -> {
                transactionDao.updateTransaction(batchTransaction);
                for (Transaction child : childTransactions) {
                    transactionDao.updateTransaction(child);
                }
            });

            throw new RuntimeException(e);
        }
    }

    @Override
    public TransactionDto btbBankPayout(IntasendBankPayoutDto intasendBankPayoutDto) {
        LocalDateTime now = LocalDateTime.now();
        String batchRef = System.currentTimeMillis() + "_" + UUID.randomUUID() + "_MAG_BANK_BATCH";

        Wallet wallet = walletDao.getWalletById(intasendBankPayoutDto.getWalletId());

        BigDecimal totalAmount = intasendBankPayoutDto.getTransactions().stream()
                .map(IntasendBankPayoutDto.IntasendBankPayoutTransactionDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String recipients = intasendBankPayoutDto.getTransactions().stream()
                .map(txn -> "Bank:" + txn.getBankCode() + " A/C:" + txn.getAccount())
                .reduce((a, b) -> a + ", " + b)
                .orElse("N/A");

        Transaction batchTransaction = Transaction.builder()
                .transactionRef(batchRef)
                .sender(wallet.getName())
                .method(TransactionMethod.INTASEND_BANK_TRANSFER)
                .type("DEBIT")
                .currency(intasendBankPayoutDto.getCurrency())
                .amount(totalAmount)
                .status("PENDING")
                .narration("Bank Payout Batch: " + intasendBankPayoutDto.getTransactions().size() + " transaction(s) to " + recipients)
                .hasBatch(true)
                .createdAt(now)
                .updatedAt(now)
                .callbacks(new ArrayList<>())
                .transactionMetaData(new ArrayList<>())
                .batchTransactions(new ArrayList<>())
                .wallet(wallet)
                .build();

        List<Transaction> childTransactions = new ArrayList<>();
        for (IntasendBankPayoutDto.IntasendBankPayoutTransactionDto txnDto : intasendBankPayoutDto.getTransactions()) {
            String childRef = batchRef + "_CHILD_" + UUID.randomUUID();

            Transaction childTransaction = Transaction.builder()
                    .transactionRef(childRef)
                    .sender("Bank Code: " + txnDto.getBankCode() + " - Account: " + txnDto.getAccount())
                    .method(TransactionMethod.INTASEND_BANK_TRANSFER)
                    .type("DEBIT")
                    .currency(intasendBankPayoutDto.getCurrency())
                    .amount(txnDto.getAmount())
                    .status("PENDING")
                    .narration(txnDto.getNarration())
                    .hasBatch(false)
                    .parentTransaction(batchTransaction)
                    .createdAt(now)
                    .updatedAt(now)
                    .callbacks(new ArrayList<>())
                    .transactionMetaData(new ArrayList<>())
                    .wallet(wallet)
                    .build();

            childTransactions.add(childTransaction);
        }

        batchTransaction.setBatchTransactions(childTransactions);

        // Batch parent + all children committed together, independently of the failure-handling
        // update below, so they survive even if the Intasend call fails.
        transactionTemplate.executeWithoutResult(status -> {
            transactionDao.createTransaction(batchTransaction);
            for (Transaction child : childTransactions) {
                transactionDao.createTransaction(child);
            }
        });

        try {
            intasendBankPayout(batchTransaction, intasendBankPayoutDto);

            pendingTransactions.put(batchTransaction.getId(), now);

            return transactionDtoMapper.toTransactionDto(batchTransaction);

        } catch (Exception e) {
            batchTransaction.setStatus("FAILED");
            batchTransaction.setFailureReason(e.getMessage());
            batchTransaction.setUpdatedAt(LocalDateTime.now());

            for (Transaction child : childTransactions) {
                child.setStatus("FAILED");
                child.setFailureReason("Bank Payout batch initiation failed: " + e.getMessage());
                child.setUpdatedAt(LocalDateTime.now());
            }

            transactionTemplate.executeWithoutResult(status -> {
                transactionDao.updateTransaction(batchTransaction);
                for (Transaction child : childTransactions) {
                    transactionDao.updateTransaction(child);
                }
            });

            throw new RuntimeException(e);
        }
    }

    @Override
    public TransactionDto approveSendMoneyTransaction(String transactionTrackingId) {
        Transaction batchTransaction = transactionDao.getTransactionByIntasendTrackingId(transactionTrackingId);
        
        if (batchTransaction == null) {
            throw new RuntimeException("Transaction not found with tracking ID: " + transactionTrackingId);
        }
        
        if (!batchTransaction.getHasBatch()) {
            throw new RuntimeException("Transaction is not a batch transaction");
        }
        
        if (batchTransaction.getTransactionMetaData() == null || batchTransaction.getTransactionMetaData().isEmpty()) {
            throw new RuntimeException("No initiation metadata found for batch. Cannot approve.");
        }
        
        try {
            TransactionMetaData initiationMetadata = batchTransaction.getTransactionMetaData().stream()
                    .filter(meta -> "B2C_BATCH_INITIATION_RESPONSE".equals(meta.getType()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("B2C initiation metadata not found"));
            
            Gson gson = new Gson();
            Map<String, Object> initiationResponse = gson.fromJson(initiationMetadata.getBody(), Map.class);
            
            Map<String, Object> walletData = (Map<String, Object>) initiationResponse.get("wallet");
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) initiationResponse.get("transactions");
            
            Map<String, Object> walletInfo = Map.of(
                    "label", walletData.get("wallet_id").toString(),
                    "can_disburse", walletData.get("can_disburse"),
                    "wallet_type", walletData.get("wallet_type").toString(),
                    "available_balance", walletData.get("available_balance").toString()
            );
            
            List<Map<String, Object>> transactionsList = new ArrayList<>();
            for (Map<String, Object> txn : transactions) {
                Map<String, Object> txnItem = Map.of(
                        "account", txn.get("account").toString(),
                        "amount", txn.get("amount").toString()
                );
                transactionsList.add(txnItem);
            }
            
            Map<String, Object> requestBody = Map.of(
                    "wallet", walletInfo,
                    "transactions", transactionsList,
                    "tracking_id", transactionTrackingId
            );
            
            String jsonBody = gson.toJson(requestBody);
            
            log.info("Approving send money batch - Tracking ID: {}", transactionTrackingId);
            log.debug("Approve request body: {}", requestBody);
            
            HttpRequest postRequest = HttpRequest.newBuilder()
                    .uri(new URI(sendMoneyApprovalUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + intasendSecretKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpResponse<String> postResponse = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());
            
            log.debug("Approve response status: {}", postResponse.statusCode());
            
            if (postResponse.statusCode() != HttpStatus.OK.value() && postResponse.statusCode() != HttpStatus.CREATED.value()) {
                log.error("Failed to approve batch - Status: {} Body: {}", 
                        postResponse.statusCode(), postResponse.body());
                throw new Exception("Failed to approve batch - Status: " + 
                        postResponse.statusCode() + " Body: " + postResponse.body());
            }
            
            String responseJson = postResponse.body();
            Map<String, Object> approveResponse = gson.fromJson(responseJson, Map.class);
            
            log.info("Batch approved successfully - Tracking ID: {}", transactionTrackingId);
            log.debug("Approve response: {}", approveResponse);
            
            String statusCode = approveResponse.get("status_code").toString();
            String status = approveResponse.get("status").toString();
            List<Map<String, Object>> approvedTransactions = (List<Map<String, Object>>) approveResponse.get("transactions");
            
            batchTransaction.setStatus(mapIntasendBatchStatusToTransactionStatus(statusCode, status));
            batchTransaction.setUpdatedAt(LocalDateTime.now());

            TransactionMetaData approvalMetadata = TransactionMetaData.builder()
                    .type("B2C_BATCH_APPROVAL_RESPONSE")
                    .body(responseJson)
                    .createdAt(LocalDateTime.now())
                    .transaction(batchTransaction)
                    .build();

            transactionTemplate.executeWithoutResult(txStatus -> {
                transactionDao.updateTransaction(batchTransaction);
                transactionDao.createTransactionMetaData(approvalMetadata);

                for (int i = 0; i < batchTransaction.getBatchTransactions().size() && i < approvedTransactions.size(); i++) {
                    Transaction childTxn = batchTransaction.getBatchTransactions().get(i);
                    Map<String, Object> approvedTxn = approvedTransactions.get(i);

                    String txnStatusCode = approvedTxn.get("status_code").toString();
                    String txnStatus = approvedTxn.get("status").toString();

                    if (approvedTxn.containsKey("charge") && approvedTxn.get("charge") != null) {
                        childTxn.setFee(new BigDecimal(approvedTxn.get("charge").toString()));
                    }

                    childTxn.setStatus(mapIntasendTransactionStatusToTransactionStatus(txnStatusCode, txnStatus));
                    childTxn.setUpdatedAt(LocalDateTime.now());

                    TransactionMetaData childApprovalMetadata = TransactionMetaData.builder()
                            .type("B2C_TRANSACTION_APPROVAL_RESPONSE")
                            .body(gson.toJson(approvedTxn))
                            .createdAt(LocalDateTime.now())
                            .transaction(childTxn)
                            .build();

                    transactionDao.updateTransaction(childTxn);
                    transactionDao.createTransactionMetaData(childApprovalMetadata);

                    log.debug("Child transaction {} approved with status: {}",
                            childTxn.getTransactionRef(), childTxn.getStatus());
                }
            });

            log.info("Batch approval complete - Batch: {}, Status: {}, Children updated: {}",
                    batchTransaction.getTransactionRef(), batchTransaction.getStatus(),
                    batchTransaction.getBatchTransactions().size());

            return transactionDtoMapper.toTransactionDto(batchTransaction);

        } catch (Exception e) {
            log.error("Error approving batch transaction: {}", transactionTrackingId, e);

            batchTransaction.setStatus("FAILED");
            batchTransaction.setFailureReason("Approval failed: " + e.getMessage());
            batchTransaction.setUpdatedAt(LocalDateTime.now());

            for (Transaction child : batchTransaction.getBatchTransactions()) {
                child.setStatus("FAILED");
                child.setFailureReason("Batch approval failed: " + e.getMessage());
                child.setUpdatedAt(LocalDateTime.now());
            }

            transactionTemplate.executeWithoutResult(txStatus -> {
                transactionDao.updateTransaction(batchTransaction);
                for (Transaction child : batchTransaction.getBatchTransactions()) {
                    transactionDao.updateTransaction(child);
                }
            });

            throw new RuntimeException("Failed to approve batch: " + e.getMessage(), e);
        }
    }

    @Override
    public TransactionDto reconcileCollectionTransaction(Long id) throws Exception {
        Transaction transaction = transactionDao.getTransactionById(id);
        if (transaction == null) {
            throw new RuntimeException("Transaction not found with ID: " + id);
        }

        TransactionMethod method = transaction.getMethod();
        if (method != TransactionMethod.INTASEND_MPESA_STK && method != TransactionMethod.INTASEND_CHECKOUT_LINK) {
            throw new RuntimeException("Transaction " + id + " has method " + method +
                    " - not a collection transaction. Use reconcileSendMoneyTransaction instead.");
        }

        if ("COMPLETED".equals(transaction.getStatus()) || "FAILED".equals(transaction.getStatus())) {
            log.debug("Transaction {} already settled with status {} - skipping Intasend reconciliation call", id, transaction.getStatus());
            return transactionDtoMapper.toTransactionDto(transaction);
        }

        if (transaction.getInvoiceId() == null) {
            throw new RuntimeException("Transaction " + id + " has no invoice_id yet - it never reached Intasend");
        }

        Gson gson = new Gson();
        String requestBody = gson.toJson(Map.of("invoice_id", transaction.getInvoiceId()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(paymentStatusUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + intasendSecretKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != HttpStatus.OK.value()) {
            throw new RuntimeException("Intasend status check failed - HTTP " + response.statusCode() + ": " + response.body());
        }

        Map<String, Object> responseMap = gson.fromJson(response.body(), Map.class);
        Map<String, Object> invoice = (Map<String, Object>) responseMap.get("invoice");

        if (invoice == null) {
            throw new RuntimeException("No invoice data returned by Intasend for invoice_id: " + transaction.getInvoiceId());
        }

        // Reuse the real webhook handler by synthesizing the same payload shape it expects,
        // instead of duplicating the state-mapping logic here.
        Map<String, Object> callbackPayload = new HashMap<>();
        callbackPayload.put("api_ref", invoice.get("api_ref"));
        callbackPayload.put("state", invoice.get("state"));
        callbackPayload.put("charges", invoice.getOrDefault("charges", "0"));
        callbackPayload.put("provider", invoice.getOrDefault("provider", ""));
        callbackPayload.put("account", invoice.getOrDefault("account", ""));
        callbackPayload.put("currency", invoice.getOrDefault("currency", transaction.getCurrency()));
        callbackPayload.put("invoice_id", invoice.getOrDefault("invoice_id", transaction.getInvoiceId()));
        if (invoice.get("failed_reason") != null) {
            callbackPayload.put("failed_reason", invoice.get("failed_reason"));
        }
        if (invoice.get("clearing_status") != null) {
            callbackPayload.put("clearing_status", invoice.get("clearing_status"));
        }

        IntasendTransactionService self = applicationContext.getBean(IntasendTransactionService.class);
        TransactionDto result = self.handleCallback(callbackPayload);

        if (result == null) {
            throw new RuntimeException("Reconciliation call to Intasend succeeded but callback processing failed for transaction " + id);
        }

        return result;
    }

    @Override
    public TransactionDto reconcileSendMoneyTransaction(Long id) throws Exception {
        Transaction transaction = transactionDao.getTransactionById(id);
        if (transaction == null) {
            throw new RuntimeException("Transaction not found with ID: " + id);
        }

        TransactionMethod method = transaction.getMethod();
        if (method == TransactionMethod.INTASEND_MPESA_STK || method == TransactionMethod.INTASEND_CHECKOUT_LINK) {
            throw new RuntimeException("Transaction " + id + " has method " + method +
                    " - not a send money transaction. Use reconcileCollectionTransaction instead.");
        }

        if ("COMPLETED".equals(transaction.getStatus()) || "FAILED".equals(transaction.getStatus())) {
            log.debug("Transaction {} already settled with status {} - skipping Intasend reconciliation call", id, transaction.getStatus());
            return transactionDtoMapper.toTransactionDto(transaction);
        }

        if (transaction.getIntasendTrackingId() == null) {
            throw new RuntimeException("Transaction " + id + " has no tracking_id yet - it never reached Intasend");
        }

        Gson gson = new Gson();
        String requestBody = gson.toJson(Map.of("tracking_id", transaction.getIntasendTrackingId()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(intasendSendMoneyStatusUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + intasendSecretKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != HttpStatus.OK.value()) {
            throw new RuntimeException("Intasend status check failed - HTTP " + response.statusCode() + ": " + response.body());
        }

        Map<String, Object> responseData = gson.fromJson(response.body(), Map.class);
        // The status response doesn't echo tracking_id back, but handleSendMoneyCallback needs
        // it to look the transaction up - it's the same value we just queried with.
        responseData.put("tracking_id", transaction.getIntasendTrackingId());

        IntasendTransactionService self = applicationContext.getBean(IntasendTransactionService.class);
        TransactionDto result = self.handleSendMoneyCallback(responseData);

        if (result == null) {
            throw new RuntimeException("Reconciliation call to Intasend succeeded but callback processing failed for transaction " + id);
        }

        return result;
    }

    // Bulk safety-net reconciliation for send-money batches: a DB scan (not tied to the
    // in-memory pendingTransactions map below, which is limited to 5 minutes post-creation and
    // is wiped on restart), so this still catches batches that missed their webhook due to
    // longer delays or a restart in between.
    @Scheduled(fixedDelay = 60000)
    public void reconcilePendingSendMoneyBatches() {
        List<Transaction> pendingBatches = transactionDao.getPendingSendMoneyBatches();

        if (pendingBatches.isEmpty()) {
            return;
        }

        log.debug("Bulk reconciling {} pending send money batch(es)", pendingBatches.size());

        IntasendTransactionService self = applicationContext.getBean(IntasendTransactionService.class);

        for (Transaction batch : pendingBatches) {
            try {
                self.reconcileSendMoneyTransaction(batch.getId());
            } catch (Exception e) {
                log.error("Bulk reconciliation failed for send money batch {}", batch.getId(), e);
            }
        }
    }

    // Same safety net as reconcilePendingSendMoneyBatches, for collection transactions
    // (INTASEND_MPESA_STK / INTASEND_CHECKOUT_LINK) instead of send-money batches.
    @Scheduled(fixedDelay = 60000)
    public void reconcilePendingCollectionTransactions() {
        List<Transaction> pendingCollections = transactionDao.getPendingCollectionTransactions();

        if (pendingCollections.isEmpty()) {
            return;
        }

        log.debug("Bulk reconciling {} pending collection transaction(s)", pendingCollections.size());

        IntasendTransactionService self = applicationContext.getBean(IntasendTransactionService.class);

        for (Transaction transaction : pendingCollections) {
            try {
                self.reconcileCollectionTransaction(transaction.getId());
            } catch (Exception e) {
                log.error("Bulk reconciliation failed for collection transaction {}", transaction.getId(), e);
            }
        }
    }

    // clearing_status can still change after a transaction is COMPLETED (e.g. moving to
    // AVAILABLE later), so this keeps checking completed collection transactions until it
    // settles - lower urgency than the reconciliation jobs above, hence the longer interval.
    @Scheduled(fixedDelay = 120000)
    public void updatePendingClearingStatuses() {
        List<Transaction> pending = transactionDao.getCollectionTransactionsPendingClearingStatus();

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Checking clearing status for {} completed collection transaction(s)", pending.size());

        for (Transaction transaction : pending) {
            try {
                updateClearingStatus(transaction, Map.of());
            } catch (Exception e) {
                log.error("Failed to update clearing status for transaction {}", transaction.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void pollPendingTransactions() {
        if (pendingTransactions.isEmpty()) {
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        List<Long> completedTransactions = new ArrayList<>();
        
        for (Map.Entry<Long, LocalDateTime> entry : pendingTransactions.entrySet()) {
            Long transactionId = entry.getKey();
            LocalDateTime createdAt = entry.getValue();
            
            long minutesElapsed = java.time.Duration.between(createdAt, now).toMinutes();
            
            if (minutesElapsed > 5) {
                log.warn("Transaction {} polling timeout after 5 minutes - removing from queue", transactionId);
                completedTransactions.add(transactionId);
                continue;
            }
            
            try {
                Transaction transaction = transactionDao.getTransactionById(transactionId);
                
                if (transaction == null) {
                    log.error("Transaction {} not found during polling", transactionId);
                    completedTransactions.add(transactionId);
                    continue;
                }
                
                String status = transaction.getStatus();
                
                if (status.equals("COMPLETED") || status.equals("FAILED")) {
                    log.info("Transaction {} settled: {}", transactionId, status);
                    completedTransactions.add(transactionId);
                    continue;
                }
                
                if (minutesElapsed >= 1) {
                    log.debug("Fallback polling Intasend API for transaction {}", transactionId);
                    
                    if (transaction.getInvoiceId() != null) {
                        pollIntasendTransactionStatus(transaction);
                    } else if (transaction.getIntasendTrackingId() != null && transaction.getHasBatch()) {
                        pollIntasendSendMoneyStatus(transaction);
                    } else {
                        log.warn("Transaction {} has no invoice_id or tracking_id - cannot poll", transactionId);
                        completedTransactions.add(transactionId);
                    }
                }

                intasendWalletService.syncWallet(transaction.getWallet().getId());
                
            } catch (Exception e) {
                log.error("Error polling transaction {}", transactionId, e);
            }
        }
        
        completedTransactions.forEach(pendingTransactions::remove);
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
            Transaction transaction = resolveTransactionFromCallback(data);

            if (transaction == null) {
                log.error("Transaction not found for callback: {}", data);
                return null;
            }

            TransactionMethod method = transaction.getMethod();

            if (method == TransactionMethod.INTASEND_MPESA_STK || method == TransactionMethod.INTASEND_CHECKOUT_LINK) {
                return processCollectionCallback(transaction, data);
            } else {
                return processSendMoneyCallback(transaction, data);
            }

        } catch (Exception e) {
            log.error("Error processing callback", e);
            return null;
        }
    }

    // Collection callbacks (api_ref) are keyed by api_ref; send-money callbacks (tracking_id)
    // are keyed by tracking_id - try whichever key the payload actually carries.
    private Transaction resolveTransactionFromCallback(Map<String, Object> data) {
        if (data.get("api_ref") != null) {
            return transactionDao.getTransactionByReference(data.get("api_ref").toString());
        }
        if (data.get("tracking_id") != null) {
            return transactionDao.getTransactionByIntasendTrackingId(data.get("tracking_id").toString());
        }
        return null;
    }

    private TransactionDto processCollectionCallback(Transaction transaction, Map<String, Object> data) {
        LocalDateTime now = LocalDateTime.now();

        Gson gson = new Gson();
        String jsonBody = gson.toJson(data);

        TransactionCallback transactionCallback = TransactionCallback.builder()
                .transaction(transaction)
                .body(jsonBody)
                .createdAt(now)
                .build();

        transactionDao.createTransactionCallback(transactionCallback);

        String state = data.get("state").toString().toLowerCase();
        String charges = data.get("charges").toString();
        String provider = data.get("provider").toString();
        String account = data.get("account").toString();
        String currency = data.get("currency").toString();
        String invoiceId = data.get("invoice_id").toString();

        transaction.setProvider(provider);
        transaction.setSender(account);
        transaction.setCurrency(currency);
        transaction.setInvoiceId(invoiceId);
        transaction.setUpdatedAt(now);

        switch (state) {
            case "complete":
                transaction.setStatus("COMPLETED");
                transaction.setFee(new BigDecimal(charges));
                pendingTransactions.remove(transaction.getId());
                break;

            case "cancelled":
            case "failed":
                transaction.setStatus("FAILED");
                if (data.containsKey("failed_reason")) {
                    transaction.setFailureReason(data.get("failed_reason").toString());
                }
                pendingTransactions.remove(transaction.getId());
                break;

            case "processing":
                transaction.setStatus("PROCESSING");
                break;

            default:
                log.warn("Unknown transaction state: {} for transaction {}", state, transaction.getTransactionRef());
        }

        transactionDao.updateTransaction(transaction);
        intasendWalletService.syncWallet(transaction.getWallet().getId());

        if ("COMPLETED".equals(transaction.getStatus())) {
            try {
                updateClearingStatus(transaction, data);
            } catch (Exception e) {
                log.error("Failed to update clearing status for transaction {}", transaction.getTransactionRef(), e);
            }
        }

        if ("COMPLETED".equals(transaction.getStatus())) {
            publishTransactionEvent(transaction, "transaction.completed");
        } else if ("FAILED".equals(transaction.getStatus())) {
            publishTransactionEvent(transaction, "transaction.failed");
        }

        log.info("Callback processed for transaction {}: status={}", transaction.getTransactionRef(), transaction.getStatus());

        return transactionDtoMapper.toTransactionDto(transaction);
    }

    // clearing_status isn't in the webhook or reconciliation payload the same way state is - it
    // only comes back from the invoice status endpoint. reconcileCollectionTransaction already
    // has it in hand from its own status check and passes it through `data`; a real incoming
    // webhook doesn't carry it at all, so this fetches it fresh in that case.
    private void updateClearingStatus(Transaction transaction, Map<String, Object> data) throws Exception {
        String clearingStatus = data.get("clearing_status") != null
                ? data.get("clearing_status").toString()
                : fetchClearingStatus(transaction.getInvoiceId());

        if (clearingStatus == null || clearingStatus.equals(transaction.getClearingStatus())) {
            return;
        }

        transaction.setClearingStatus(clearingStatus);
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionTemplate.executeWithoutResult(status -> transactionDao.updateTransaction(transaction));

        log.info("Clearing status updated for transaction {}: {}", transaction.getTransactionRef(), clearingStatus);

        if ("AVAILABLE".equals(clearingStatus)) {
            publishTransactionEvent(transaction, "transaction.cleared");
        }
    }

    // Fire-and-forget: a broker outage must never fail payment processing itself, so any
    // publish failure is logged and swallowed rather than propagated. Covers all three
    // producers of a clearing/settlement change - the real webhook (processCollectionCallback),
    // the manual reconcile endpoint (reconcileCollectionTransaction, which re-enters through
    // handleCallback), and the scheduled clearing-status sweep (updatePendingClearingStatuses) -
    // since all three ultimately call updateClearingStatus.
    private void publishTransactionEvent(Transaction transaction, String routingKey) {
        try {
            TransactionEventDto event = TransactionEventDto.builder()
                    .id(transaction.getId())
                    .reference(transaction.getTransactionRef())
                    .walletId(transaction.getWallet() != null ? transaction.getWallet().getId() : null)
                    .amount(transaction.getAmount() != null ? String.valueOf(transaction.getAmount()) : null)
                    .currency(transaction.getCurrency())
                    .fee(transaction.getFee() != null ? String.valueOf(transaction.getFee()) : null)
                    .status(transaction.getStatus())
                    .narration(transaction.getNarration())
                    .build();
            rabbitTemplate.convertAndSend(RabbitConfig.TRANSACTIONS_EXCHANGE, routingKey, event);
            log.info("Published {} for transaction {}", routingKey, transaction.getTransactionRef());
        } catch (Exception e) {
            log.error("Failed to publish {} for transaction {}: {}", routingKey, transaction.getTransactionRef(), e.getMessage(), e);
        }
    }

    private String fetchClearingStatus(String invoiceId) throws Exception {
        Gson gson = new Gson();
        String requestBody = gson.toJson(Map.of("invoice_id", invoiceId));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(paymentStatusUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + intasendSecretKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != HttpStatus.OK.value()) {
            log.warn("Failed to fetch clearing status for invoice {}: HTTP {}", invoiceId, response.statusCode());
            return null;
        }

        Map<String, Object> responseMap = gson.fromJson(response.body(), Map.class);
        Map<String, Object> invoice = (Map<String, Object>) responseMap.get("invoice");

        return invoice != null && invoice.get("clearing_status") != null
                ? invoice.get("clearing_status").toString()
                : null;
    }

    @Override
    @Transactional
    public TransactionDto handleSendMoneyCallback(Map<String, Object> data) {
        try {
            String trackingId = data.get("tracking_id").toString();
            Transaction batchTransaction = transactionDao.getTransactionByIntasendTrackingId(trackingId);

            if (batchTransaction == null) {
                log.error("Batch transaction not found for tracking_id: {}", trackingId);
                return null;
            }

            return processSendMoneyCallback(batchTransaction, data);

        } catch (Exception e) {
            log.error("Error processing send money callback", e);
            return null;
        }
    }

    private TransactionDto processSendMoneyCallback(Transaction batchTransaction, Map<String, Object> data) {
        if (!batchTransaction.getHasBatch()) {
            log.warn("Transaction {} is not a batch transaction", batchTransaction.getTransactionRef());
        }

        LocalDateTime now = LocalDateTime.now();

        Gson gson = new Gson();
        String jsonBody = gson.toJson(data);

        TransactionCallback transactionCallback = TransactionCallback.builder()
                .transaction(batchTransaction)
                .body(jsonBody)
                .createdAt(now)
                .build();

        transactionDao.createTransactionCallback(transactionCallback);

        String statusCode = data.get("status_code").toString();
        String status = data.get("status").toString();

        List<Map<String, Object>> transactions = (List<Map<String, Object>>) data.get("transactions");

        String previousStatus = batchTransaction.getStatus();
        String newBatchStatus = mapIntasendBatchStatusToTransactionStatus(statusCode, status);
        batchTransaction.setStatus(newBatchStatus);
        batchTransaction.setUpdatedAt(now);

        if (data.containsKey("wallet")) {
            Map<String, Object> walletData = (Map<String, Object>) data.get("wallet");
            log.debug("Wallet balance - Current: {}, Available: {}",
                walletData.get("current_balance"), walletData.get("available_balance"));
        }

        transactionDao.updateTransaction(batchTransaction);

        log.info("Send money callback processed for batch {} - Status: {} -> {}",
                batchTransaction.getIntasendTrackingId(), previousStatus, newBatchStatus);

        if (batchTransaction.getHasBatch() && transactions != null && !transactions.isEmpty()) {
            for (Map<String, Object> txnData : transactions) {
                String intasendTxnId = txnData.get("transaction_id").toString();

                Transaction childTransaction = batchTransaction.getBatchTransactions().stream()
                        .filter(child -> intasendTxnId.equals(child.getIntasendTransactionId()))
                        .findFirst()
                        .orElse(null);

                if (childTransaction != null) {
                    String txnStatusCode = txnData.get("status_code").toString();
                    String txnStatus = txnData.get("status").toString();

                    String previousChildStatus = childTransaction.getStatus();
                    String newChildStatus = mapIntasendTransactionStatusToTransactionStatus(txnStatusCode, txnStatus);

                    childTransaction.setStatus(newChildStatus);
                    childTransaction.setUpdatedAt(now);

                    if (txnData.containsKey("amount") && txnData.get("amount") != null) {
                        childTransaction.setAmount(new BigDecimal(txnData.get("amount").toString()));
                    }

                    if (txnData.containsKey("charge") && txnData.get("charge") != null) {
                        childTransaction.setFee(new BigDecimal(txnData.get("charge").toString()));
                    }

                    if (txnData.containsKey("provider") && txnData.get("provider") != null) {
                        childTransaction.setProvider(txnData.get("provider").toString());
                    }

                    transactionDao.updateTransaction(childTransaction);

                    log.debug("Child transaction {} updated: {} -> {}",
                            intasendTxnId, previousChildStatus, newChildStatus);

                    // Unlike collection settlement, nothing downstream previously learned a
                    // disbursement settled at all - LigiopenBackendApp has no other way to
                    // correlate a batch's outcome back to the individual recipients (teams)
                    // that made it up. Publish per CHILD, not per batch, so each recipient's
                    // own settlement result is independently addressable - the caller
                    // correlates back to its own record via this child's narration, which it
                    // set itself when building the batch request.
                    if ("COMPLETED".equals(newChildStatus)) {
                        publishTransactionEvent(childTransaction, "payout.completed");
                    } else if ("FAILED".equals(newChildStatus)) {
                        publishTransactionEvent(childTransaction, "payout.failed");
                    }
                } else {
                    log.warn("Child transaction not found for intasend_transaction_id: {}", intasendTxnId);
                }
            }
        }

        log.info("Send money callback complete - Batch: {}, Status: {}, Children updated: {}",
                batchTransaction.getTransactionRef(), batchTransaction.getStatus(),
                transactions != null ? transactions.size() : 0);

        return transactionDtoMapper.toTransactionDto(batchTransaction);
    }

    @Override
    public TransactionDto handleReversalCallback(Map<String, Object> data) {
        return null;
    }

    @Override
    public TransactionDto handleWalletTransferCallback(Map<String, Object> data) {
        return null;
    }

    private Transaction intasendCheckout(Transaction transaction, String redirectUrl) throws Exception {
        TransactionMethod method = transaction.getMethod();

        if (method == TransactionMethod.INTASEND_MPESA_STK) {
            return intasendMpesaStkCheckout(transaction);
        } else {
            return intasendCheckoutLink(transaction, redirectUrl);
        }
    }

    // Intasend M-Pesa STK push checkout
    private Transaction intasendMpesaStkCheckout(Transaction transaction) throws Exception {

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

        transactionTemplate.executeWithoutResult(status -> transactionDao.updateTransaction(transaction));

        return transaction;

    }

    // Intasend hosted checkout link
    private Transaction intasendCheckoutLink(Transaction transaction, String redirectUrl) throws URISyntaxException, IOException, InterruptedException {
        Map<String, Object> requestBody = new java.util.HashMap<>(Map.of(
                "amount", String.valueOf(transaction.getAmount()),
                "phone_number", transaction.getSender(),
                "wallet_id", transaction.getWallet().getIntasendWalletId(),
                "api_ref", transaction.getTransactionRef()
        ));
        // Intasend sends the browser here once payment completes - without it, there's no way
        // for the caller's own app/site to be notified except by polling, since the hosted
        // checkout page has no "back to merchant" affordance of its own.
        if (org.springframework.util.StringUtils.hasText(redirectUrl)) {
            requestBody.put("redirect_url", redirectUrl);
        }

        Gson gson = new Gson();
        String jsonBody = gson.toJson(requestBody);

        log.debug("Intasend checkout link request: {}", requestBody);

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(new URI(checkoutUrl))
                .header("Content-Type", "application/json")
                .header("X-IntaSend-Public-API-Key", intasendPublicKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> postResponse = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());

        log.debug("Intasend checkout link response {}: ", postResponse);

        if (postResponse.statusCode() != HttpStatus.OK.value() && postResponse.statusCode() != HttpStatus.CREATED.value()) {
            log.error("Failed to initialize checkout link::: Status code {} response body:: {}", postResponse.statusCode(), postResponse.body());
            throw new RuntimeException("Failed to initialize checkout link::: Status code " + postResponse.statusCode() + " response body:: " + postResponse.body());
        }

        String jsonString = postResponse.body();
        Map<String, Object> responseMap = gson.fromJson(jsonString, Map.class);

        String hostedCheckoutUrl = (String) responseMap.get("url");

        transaction.setCheckoutLink(hostedCheckoutUrl);

        transactionTemplate.executeWithoutResult(status -> transactionDao.updateTransaction(transaction));

        return transaction;
    }

    private Transaction intasendB2CMpesa(Transaction batchTransaction, IntasendMpesaBTCDto dto) throws Exception {
        
        List<Map<String, Object>> transactionItems = new ArrayList<>();
        int index = 0;
        for (Transaction childTxn : batchTransaction.getBatchTransactions()) {
            IntasendMpesaBTCDto.MpesaBTCTransactionDto txnDto = dto.getTransactions().get(index);
            Map<String, Object> item = Map.of(
                    "account", txnDto.getRecipientPhoneNumber(),
                    "amount", txnDto.getAmount().toString(),
                    "narrative", txnDto.getNarration() != null ? txnDto.getNarration() : "",
                    "requires_approval", true
            );
            transactionItems.add(item);
            index++;
        }
        
        Map<String, Object> requestBody = Map.of(
                "currency", dto.getCurrency(),
                "provider", "MPESA-B2C",
                "wallet_id", batchTransaction.getWallet().getIntasendWalletId(),
                "transactions", transactionItems
        );

        Gson gson = new Gson();
        String jsonBody = gson.toJson(requestBody);

        log.info("Intasend B2C batch payment - {} transaction(s) - Batch Ref: {}", 
                dto.getTransactions().size(), batchTransaction.getTransactionRef());
        log.debug("Intasend B2C request body: {}", requestBody);

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(new URI(sendMoneyUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + intasendSecretKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> postResponse = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());

        log.debug("Intasend B2C response status: {}", postResponse.statusCode());

        if (postResponse.statusCode() != HttpStatus.OK.value() && postResponse.statusCode() != HttpStatus.CREATED.value()) {
            log.error("Failed to initiate B2C batch - Status: {} Body: {}", 
                postResponse.statusCode(), postResponse.body());
            throw new Exception("Failed to initiate B2C batch - Status: " + 
                postResponse.statusCode() + " Body: " + postResponse.body());
        }

        String jsonString = postResponse.body();
        Map<String, Object> responseMap = gson.fromJson(jsonString, Map.class);

        log.info("Intasend B2C batch created successfully");
        log.debug("Intasend B2C response: {}", responseMap);

        String trackingId = responseMap.get("tracking_id").toString();
        String statusCode = responseMap.get("status_code").toString();
        String status = responseMap.get("status").toString();
        
        Map<String, Object> walletData = (Map<String, Object>) responseMap.get("wallet");
        String currentBalance = walletData.get("current_balance").toString();
        String availableBalance = walletData.get("available_balance").toString();
        
        List<Map<String, Object>> intasendTransactions = (List<Map<String, Object>>) responseMap.get("transactions");

        batchTransaction.setIntasendTrackingId(trackingId);
        batchTransaction.setStatus(mapIntasendBatchStatusToTransactionStatus(statusCode, status));
        batchTransaction.setUpdatedAt(LocalDateTime.now());
        
        TransactionMetaData batchMetaData = TransactionMetaData.builder()
                .type("B2C_BATCH_INITIATION_RESPONSE")
                .body(jsonString)
                .createdAt(LocalDateTime.now())
                .transaction(batchTransaction)
                .build();

        transactionTemplate.executeWithoutResult(txStatus -> {
            transactionDao.updateTransaction(batchTransaction);
            transactionDao.createTransactionMetaData(batchMetaData);

            for (int i = 0; i < batchTransaction.getBatchTransactions().size() && i < intasendTransactions.size(); i++) {
                Transaction childTxn = batchTransaction.getBatchTransactions().get(i);
                Map<String, Object> intasendTxn = intasendTransactions.get(i);

                String intasendTxnId = intasendTxn.get("transaction_id").toString();
                String txnStatusCode = intasendTxn.get("status_code").toString();
                String txnStatus = intasendTxn.get("status").toString();

                childTxn.setIntasendTransactionId(intasendTxnId);
                childTxn.setStatus(mapIntasendTransactionStatusToTransactionStatus(txnStatusCode, txnStatus));
                childTxn.setUpdatedAt(LocalDateTime.now());

                TransactionMetaData childMetaData = TransactionMetaData.builder()
                        .type("B2C_TRANSACTION_INITIATION_RESPONSE")
                        .body(gson.toJson(intasendTxn))
                        .createdAt(LocalDateTime.now())
                        .transaction(childTxn)
                        .build();

                transactionDao.updateTransaction(childTxn);
                transactionDao.createTransactionMetaData(childMetaData);

                log.debug("Child transaction {} mapped to Intasend transaction_id: {}, status: {}",
                        childTxn.getTransactionRef(), intasendTxnId, childTxn.getStatus());
            }
        });

        log.info("B2C batch saved - Tracking ID: {}, Status: {}, Wallet Balance: {} (Available: {}), Child Transactions: {}", 
                trackingId, batchTransaction.getStatus(), currentBalance, availableBalance, batchTransaction.getBatchTransactions().size());

        return batchTransaction;
    }
    
    private Transaction intasendB2BPayBill(Transaction batchTransaction, IntasendMpesaBTBPaybillDto dto) throws Exception {
        
        List<Map<String, Object>> transactionItems = new ArrayList<>();
        int index = 0;
        for (Transaction childTxn : batchTransaction.getBatchTransactions()) {
            IntasendMpesaBTBPaybillDto.MpesaBTBPaybillTransactionDto txnDto = dto.getTransactions().get(index);
            Map<String, Object> item = Map.of(
                    "amount", txnDto.getAmount().toString(),
                    "account_type", "PayBill",
                    "account", txnDto.getPaybillNumber(),
                    "account_reference", txnDto.getAccountReference(),
                    "api_ref", childTxn.getTransactionRef(),
                    "requires_approval", dto.getRequiresApproval() != null ? dto.getRequiresApproval() : false
            );
            transactionItems.add(item);
            index++;
        }
        
        Map<String, Object> requestBody = Map.of(
                "currency", dto.getCurrency(),
                "provider", "MPESA-B2B",
                "wallet_id", batchTransaction.getWallet().getIntasendWalletId(),
                "transactions", transactionItems
        );

        Gson gson = new Gson();
        String jsonBody = gson.toJson(requestBody);

        log.info("Intasend B2B PayBill batch - {} transaction(s) - Batch Ref: {}", 
                dto.getTransactions().size(), batchTransaction.getTransactionRef());
        log.debug("Intasend B2B PayBill request body: {}", requestBody);

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(new URI(sendMoneyUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + intasendSecretKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> postResponse = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());

        log.debug("Intasend B2B PayBill response status: {}", postResponse.statusCode());

        if (postResponse.statusCode() != HttpStatus.OK.value() && postResponse.statusCode() != HttpStatus.CREATED.value()) {
            log.error("Failed to initiate B2B PayBill batch - Status: {} Body: {}", 
                postResponse.statusCode(), postResponse.body());
            throw new Exception("Failed to initiate B2B PayBill batch - Status: " + 
                postResponse.statusCode() + " Body: " + postResponse.body());
        }

        String jsonString = postResponse.body();
        Map<String, Object> responseMap = gson.fromJson(jsonString, Map.class);

        log.info("Intasend B2B PayBill batch created successfully");
        log.debug("Intasend B2B PayBill response: {}", responseMap);

        String trackingId = responseMap.get("tracking_id").toString();
        String statusCode = responseMap.get("status_code").toString();
        String status = responseMap.get("status").toString();
        
        Map<String, Object> walletData = (Map<String, Object>) responseMap.get("wallet");
        String currentBalance = walletData.get("current_balance").toString();
        String availableBalance = walletData.get("available_balance").toString();
        
        List<Map<String, Object>> intasendTransactions = (List<Map<String, Object>>) responseMap.get("transactions");

        batchTransaction.setIntasendTrackingId(trackingId);
        batchTransaction.setStatus(mapIntasendBatchStatusToTransactionStatus(statusCode, status));
        batchTransaction.setUpdatedAt(LocalDateTime.now());
        
        TransactionMetaData batchMetaData = TransactionMetaData.builder()
                .type("B2B_PAYBILL_BATCH_INITIATION_RESPONSE")
                .body(jsonString)
                .createdAt(LocalDateTime.now())
                .transaction(batchTransaction)
                .build();

        transactionTemplate.executeWithoutResult(txStatus -> {
            transactionDao.updateTransaction(batchTransaction);
            transactionDao.createTransactionMetaData(batchMetaData);

            for (int i = 0; i < batchTransaction.getBatchTransactions().size() && i < intasendTransactions.size(); i++) {
                Transaction childTxn = batchTransaction.getBatchTransactions().get(i);
                Map<String, Object> intasendTxn = intasendTransactions.get(i);

                String intasendTxnId = intasendTxn.get("transaction_id").toString();
                String txnStatusCode = intasendTxn.get("status_code").toString();
                String txnStatus = intasendTxn.get("status").toString();

                childTxn.setIntasendTransactionId(intasendTxnId);
                childTxn.setStatus(mapIntasendTransactionStatusToTransactionStatus(txnStatusCode, txnStatus));
                childTxn.setUpdatedAt(LocalDateTime.now());

                TransactionMetaData childMetaData = TransactionMetaData.builder()
                        .type("B2B_PAYBILL_TRANSACTION_INITIATION_RESPONSE")
                        .body(gson.toJson(intasendTxn))
                        .createdAt(LocalDateTime.now())
                        .transaction(childTxn)
                        .build();

                transactionDao.updateTransaction(childTxn);
                transactionDao.createTransactionMetaData(childMetaData);

                log.debug("Child B2B PayBill transaction {} mapped to Intasend transaction_id: {}, status: {}",
                        childTxn.getTransactionRef(), intasendTxnId, childTxn.getStatus());
            }
        });

        log.info("B2B PayBill batch saved - Tracking ID: {}, Status: {}, Wallet Balance: {} (Available: {}), Child Transactions: {}", 
                trackingId, batchTransaction.getStatus(), currentBalance, availableBalance, batchTransaction.getBatchTransactions().size());

        return batchTransaction;
    }
    
    private Transaction intasendB2BTill(Transaction batchTransaction, IntasendMpesaBTBTillDto dto) throws Exception {
        
        List<Map<String, Object>> transactionItems = new ArrayList<>();
        int index = 0;
        for (Transaction childTxn : batchTransaction.getBatchTransactions()) {
            IntasendMpesaBTBTillDto.MpesaBTBTillTransactionDto txnDto = dto.getTransactions().get(index);
            Map<String, Object> item = Map.of(
                    "amount", txnDto.getAmount().toString(),
                    "account_type", "TillNumber",
                    "account", txnDto.getTillNumber(),
                    "api_ref", childTxn.getTransactionRef(),
                    "requires_approval", dto.getRequiresApproval() != null ? dto.getRequiresApproval() : false
            );
            transactionItems.add(item);
            index++;
        }
        
        Map<String, Object> requestBody = Map.of(
                "currency", dto.getCurrency(),
                "provider", "MPESA-B2B",
                "wallet_id", batchTransaction.getWallet().getIntasendWalletId(),
                "transactions", transactionItems
        );

        Gson gson = new Gson();
        String jsonBody = gson.toJson(requestBody);

        log.info("Intasend B2B Till batch - {} transaction(s) - Batch Ref: {}", 
                dto.getTransactions().size(), batchTransaction.getTransactionRef());
        log.debug("Intasend B2B Till request body: {}", requestBody);

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(new URI(sendMoneyUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + intasendSecretKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> postResponse = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());

        log.debug("Intasend B2B Till response status: {}", postResponse.statusCode());

        if (postResponse.statusCode() != HttpStatus.OK.value() && postResponse.statusCode() != HttpStatus.CREATED.value()) {
            log.error("Failed to initiate B2B Till batch - Status: {} Body: {}", 
                postResponse.statusCode(), postResponse.body());
            throw new Exception("Failed to initiate B2B Till batch - Status: " + 
                postResponse.statusCode() + " Body: " + postResponse.body());
        }

        String jsonString = postResponse.body();
        Map<String, Object> responseMap = gson.fromJson(jsonString, Map.class);

        log.info("Intasend B2B Till batch created successfully");
        log.debug("Intasend B2B Till response: {}", responseMap);

        String trackingId = responseMap.get("tracking_id").toString();
        String statusCode = responseMap.get("status_code").toString();
        String status = responseMap.get("status").toString();
        
        Map<String, Object> walletData = (Map<String, Object>) responseMap.get("wallet");
        String currentBalance = walletData.get("current_balance").toString();
        String availableBalance = walletData.get("available_balance").toString();
        
        List<Map<String, Object>> intasendTransactions = (List<Map<String, Object>>) responseMap.get("transactions");

        batchTransaction.setIntasendTrackingId(trackingId);
        batchTransaction.setStatus(mapIntasendBatchStatusToTransactionStatus(statusCode, status));
        batchTransaction.setUpdatedAt(LocalDateTime.now());
        
        TransactionMetaData batchMetaData = TransactionMetaData.builder()
                .type("B2B_TILL_BATCH_INITIATION_RESPONSE")
                .body(jsonString)
                .createdAt(LocalDateTime.now())
                .transaction(batchTransaction)
                .build();

        transactionTemplate.executeWithoutResult(txStatus -> {
            transactionDao.updateTransaction(batchTransaction);
            transactionDao.createTransactionMetaData(batchMetaData);

            for (int i = 0; i < batchTransaction.getBatchTransactions().size() && i < intasendTransactions.size(); i++) {
                Transaction childTxn = batchTransaction.getBatchTransactions().get(i);
                Map<String, Object> intasendTxn = intasendTransactions.get(i);

                String intasendTxnId = intasendTxn.get("transaction_id").toString();
                String txnStatusCode = intasendTxn.get("status_code").toString();
                String txnStatus = intasendTxn.get("status").toString();

                childTxn.setIntasendTransactionId(intasendTxnId);
                childTxn.setStatus(mapIntasendTransactionStatusToTransactionStatus(txnStatusCode, txnStatus));
                childTxn.setUpdatedAt(LocalDateTime.now());

                TransactionMetaData childMetaData = TransactionMetaData.builder()
                        .type("B2B_TILL_TRANSACTION_INITIATION_RESPONSE")
                        .body(gson.toJson(intasendTxn))
                        .createdAt(LocalDateTime.now())
                        .transaction(childTxn)
                        .build();

                transactionDao.updateTransaction(childTxn);
                transactionDao.createTransactionMetaData(childMetaData);

                log.debug("Child B2B Till transaction {} mapped to Intasend transaction_id: {}, status: {}",
                        childTxn.getTransactionRef(), intasendTxnId, childTxn.getStatus());
            }
        });

        log.info("B2B Till batch saved - Tracking ID: {}, Status: {}, Wallet Balance: {} (Available: {}), Child Transactions: {}", 
                trackingId, batchTransaction.getStatus(), currentBalance, availableBalance, batchTransaction.getBatchTransactions().size());

        return batchTransaction;
    }
    
    private Transaction intasendBankPayout(Transaction batchTransaction, IntasendBankPayoutDto dto) throws Exception {
        
        List<Map<String, Object>> transactionItems = new ArrayList<>();
        int index = 0;
        for (Transaction childTxn : batchTransaction.getBatchTransactions()) {
            IntasendBankPayoutDto.IntasendBankPayoutTransactionDto txnDto = dto.getTransactions().get(index);
            Map<String, Object> item = Map.of(
                    "amount", txnDto.getAmount().toString(),
                    "bank_code", txnDto.getBankCode(),
                    "account", txnDto.getAccount()
            );
            transactionItems.add(item);
            index++;
        }
        
        String provider = dto.getProvider() != null && !dto.getProvider().isEmpty() ? dto.getProvider() : "PESALINK";
        
        Map<String, Object> requestBody = Map.of(
                "currency", dto.getCurrency(),
                "provider", provider,
                "wallet_id", batchTransaction.getWallet().getIntasendWalletId(),
                "transactions", transactionItems
        );

        Gson gson = new Gson();
        String jsonBody = gson.toJson(requestBody);

        log.info("Intasend Bank Payout batch - {} transaction(s) - Provider: {} - Batch Ref: {}", 
                dto.getTransactions().size(), provider, batchTransaction.getTransactionRef());
        log.debug("Intasend Bank Payout request body: {}", requestBody);

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(new URI(sendMoneyUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + intasendSecretKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> postResponse = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());

        log.debug("Intasend Bank Payout response status: {}", postResponse.statusCode());

        if (postResponse.statusCode() != HttpStatus.OK.value() && postResponse.statusCode() != HttpStatus.CREATED.value()) {
            log.error("Failed to initiate Bank Payout batch - Status: {} Body: {}", 
                postResponse.statusCode(), postResponse.body());
            throw new Exception("Failed to initiate Bank Payout batch - Status: " + 
                postResponse.statusCode() + " Body: " + postResponse.body());
        }

        String jsonString = postResponse.body();
        Map<String, Object> responseMap = gson.fromJson(jsonString, Map.class);

        log.info("Intasend Bank Payout batch created successfully");
        log.debug("Intasend Bank Payout response: {}", responseMap);

        String trackingId = responseMap.get("tracking_id").toString();
        String statusCode = responseMap.get("status_code").toString();
        String status = responseMap.get("status").toString();
        
        Map<String, Object> walletData = (Map<String, Object>) responseMap.get("wallet");
        String currentBalance = walletData.get("current_balance").toString();
        String availableBalance = walletData.get("available_balance").toString();
        
        List<Map<String, Object>> intasendTransactions = (List<Map<String, Object>>) responseMap.get("transactions");

        batchTransaction.setIntasendTrackingId(trackingId);
        batchTransaction.setProvider(provider);
        batchTransaction.setStatus(mapIntasendBatchStatusToTransactionStatus(statusCode, status));
        batchTransaction.setUpdatedAt(LocalDateTime.now());
        
        TransactionMetaData batchMetaData = TransactionMetaData.builder()
                .type("BANK_PAYOUT_BATCH_INITIATION_RESPONSE")
                .body(jsonString)
                .createdAt(LocalDateTime.now())
                .transaction(batchTransaction)
                .build();

        transactionTemplate.executeWithoutResult(txStatus -> {
            transactionDao.updateTransaction(batchTransaction);
            transactionDao.createTransactionMetaData(batchMetaData);

            for (int i = 0; i < batchTransaction.getBatchTransactions().size() && i < intasendTransactions.size(); i++) {
                Transaction childTxn = batchTransaction.getBatchTransactions().get(i);
                Map<String, Object> intasendTxn = intasendTransactions.get(i);

                String intasendTxnId = intasendTxn.get("transaction_id").toString();
                String txnStatusCode = intasendTxn.get("status_code").toString();
                String txnStatus = intasendTxn.get("status").toString();

                if (intasendTxn.containsKey("bank_code") && intasendTxn.get("bank_code") != null) {
                    childTxn.setSender("Bank Code: " + intasendTxn.get("bank_code").toString() +
                            " - Account: " + intasendTxn.get("account").toString());
                }

                childTxn.setIntasendTransactionId(intasendTxnId);
                childTxn.setProvider(provider);
                childTxn.setStatus(mapIntasendTransactionStatusToTransactionStatus(txnStatusCode, txnStatus));
                childTxn.setUpdatedAt(LocalDateTime.now());

                TransactionMetaData childMetaData = TransactionMetaData.builder()
                        .type("BANK_PAYOUT_TRANSACTION_INITIATION_RESPONSE")
                        .body(gson.toJson(intasendTxn))
                        .createdAt(LocalDateTime.now())
                        .transaction(childTxn)
                        .build();

                transactionDao.updateTransaction(childTxn);
                transactionDao.createTransactionMetaData(childMetaData);

                log.debug("Child Bank Payout transaction {} mapped to Intasend transaction_id: {}, status: {}",
                        childTxn.getTransactionRef(), intasendTxnId, childTxn.getStatus());
            }
        });

        log.info("Bank Payout batch saved - Tracking ID: {}, Provider: {}, Status: {}, Wallet Balance: {} (Available: {}), Child Transactions: {}", 
                trackingId, provider, batchTransaction.getStatus(), currentBalance, availableBalance, batchTransaction.getBatchTransactions().size());

        return batchTransaction;
    }
    
    private void pollIntasendSendMoneyStatus(Transaction batchTransaction) {
        log.debug("Polling Intasend Send Money API for batch transaction {}", batchTransaction.getTransactionRef());
        
        try {
            if (batchTransaction.getIntasendTrackingId() == null) {
                log.warn("Cannot poll Intasend - no tracking ID for batch transaction {}", batchTransaction.getTransactionRef());
                return;
            }
            
            Map<String, String> requestBody = Map.of("tracking_id", batchTransaction.getIntasendTrackingId());
            
            Gson gson = new Gson();
            String jsonBody = gson.toJson(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(intasendSendMoneyStatusUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + intasendSecretKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.debug("Intasend Send Money polling response status: {}", response.statusCode());
            
            if (response.statusCode() == HttpStatus.OK.value()) {
                Map<String, Object> responseData = gson.fromJson(response.body(), Map.class);
                
                String statusCode = responseData.get("status_code").toString();
                String status = responseData.get("status").toString();
                List<Map<String, Object>> transactions = (List<Map<String, Object>>) responseData.get("transactions");
                
                Transaction latestBatchTransaction = transactionDao.getTransactionById(batchTransaction.getId());
                String currentBatchStatus = latestBatchTransaction != null ? latestBatchTransaction.getStatus() : null;
                
                String newBatchStatus = mapIntasendBatchStatusToTransactionStatus(statusCode, status);
                
                if (newBatchStatus != null && !newBatchStatus.equals(currentBatchStatus)) {
                    IntasendTransactionService proxy = applicationContext.getBean(IntasendTransactionService.class);
                    proxy.updateSendMoneyBatchFromPolling(batchTransaction.getId(), newBatchStatus, statusCode, status, transactions);
                    
                    log.info("Polled and updated Send Money batch {}: {} -> {}", 
                            batchTransaction.getTransactionRef(), currentBatchStatus, newBatchStatus);
                } else {
                    log.debug("Send Money batch {} status unchanged: {}", batchTransaction.getTransactionRef(), currentBatchStatus);
                }
            } else {
                log.error("Failed to poll Intasend Send Money - Status: {}, Body: {}", response.statusCode(), response.body());
            }
            
        } catch (Exception e) {
            log.error("Error polling Intasend Send Money for batch {}", batchTransaction.getTransactionRef(), e);
        }
    }
    
    private String mapIntasendBatchStatusToTransactionStatus(String statusCode, String statusDescription) {
        switch (statusCode) {
            case "BP101":
            case "BP103":
            case "BP104":
            case "BP106":
            case "BP108":
            case "BP109":
            case "BP110":
                return "PROCESSING";
            case "BC100":
                return "COMPLETED";
            case "BF102":
            case "BF105":
            case "BF107":
            case "BE111":
                return "FAILED";
            default:
                log.warn("Unknown Intasend batch status code: {} - {}", statusCode, statusDescription);
                return "PROCESSING";
        }
    }
    
    private String mapIntasendTransactionStatusToTransactionStatus(String statusCode, String statusDescription) {
        switch (statusCode) {
            case "TP101":
            case "TP102":
            case "TP104":
            case "TH107":
            case "TR109":
                return "PROCESSING";
            case "TS100":
                return "COMPLETED";
            case "TF103":
            case "TF105":
            case "TF106":
            case "TC108":
                return "FAILED";
            default:
                log.warn("Unknown Intasend transaction status code: {} - {}", statusCode, statusDescription);
                return "PROCESSING";
        }
    }
    
    private void pollIntasendTransactionStatus(Transaction transaction) {
        log.debug("Polling Intasend API for transaction {}", transaction.getTransactionRef());
        
        try {
            if (transaction.getInvoiceId() == null) {
                log.warn("Cannot poll Intasend - no invoice ID for transaction {}", transaction.getTransactionRef());
                return;
            }
            
            Map<String, String> requestBody = Map.of("invoice_id", transaction.getInvoiceId());
            
            Gson gson = new Gson();
            String jsonBody = gson.toJson(requestBody);
            
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
                
                Map<String, Object> invoice = (Map<String, Object>) responseData.get("invoice");
                
                if (invoice != null) {
                    String state = invoice.get("state").toString().toLowerCase();
                    
                    String newStatus = mapStateToStatus(state);
                    
                    Transaction latestTransaction = transactionDao.getTransactionById(transaction.getId());
                    String currentStatus = latestTransaction != null ? latestTransaction.getStatus() : null;
                    
                    if (newStatus != null && !newStatus.equals(currentStatus)) {
                        String charges = invoice.get("charges").toString();
                        String provider = invoice.get("provider").toString();
                        String account = invoice.get("account").toString();
                        String currency = invoice.get("currency").toString();
                        String invoiceId = invoice.get("invoice_id").toString();
                        String failureReason = invoice.containsKey("failed_reason") && invoice.get("failed_reason") != null 
                            ? invoice.get("failed_reason").toString() : null;
                        
                        IntasendTransactionService proxy = applicationContext.getBean(IntasendTransactionService.class);
                        proxy.updateTransactionStatusFromPolling(
                            transaction.getId(), newStatus, charges, provider, account, currency, invoiceId, failureReason);
                        
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
    
    @Override
    @Transactional
    public void updateTransactionStatusFromPolling(Long transactionId, String status, String charges, 
                                                   String provider, String account, String currency, 
                                                   String invoiceId, String failureReason) {
        Transaction transaction = transactionDao.getTransactionById(transactionId);
        if (transaction != null) {
            transaction.setProvider(provider);
            transaction.setSender(account);
            transaction.setCurrency(currency);
            transaction.setInvoiceId(invoiceId);
            transaction.setUpdatedAt(LocalDateTime.now());
            transaction.setStatus(status);
            
            if (status.equals("COMPLETED")) {
                transaction.setFee(new BigDecimal(charges));
                pendingTransactions.remove(transactionId);
            } else if (status.equals("FAILED")) {
                if (failureReason != null) {
                    transaction.setFailureReason(failureReason);
                }
                pendingTransactions.remove(transactionId);
            }
            
            transactionDao.updateTransaction(transaction);
        }
    }
    
    @Override
    @Transactional
    public void updateSendMoneyBatchFromPolling(Long batchTransactionId, String newBatchStatus, 
                                                String batchStatusCode, String batchStatusDescription,
                                                List<Map<String, Object>> transactions) {
        Transaction batchTransaction = transactionDao.getTransactionById(batchTransactionId);
        if (batchTransaction == null) {
            log.error("Batch transaction {} not found during polling update", batchTransactionId);
            return;
        }
        
        batchTransaction.setStatus(newBatchStatus);
        batchTransaction.setUpdatedAt(LocalDateTime.now());
        transactionDao.updateTransaction(batchTransaction);
        
        if (newBatchStatus.equals("COMPLETED") || newBatchStatus.equals("FAILED")) {
            pendingTransactions.remove(batchTransactionId);
        }
        
        if (batchTransaction.getHasBatch() && transactions != null && !transactions.isEmpty()) {
            for (Map<String, Object> txnData : transactions) {
                String intasendTxnId = txnData.get("transaction_id").toString();
                
                Transaction childTransaction = batchTransaction.getBatchTransactions().stream()
                        .filter(child -> intasendTxnId.equals(child.getIntasendTransactionId()))
                        .findFirst()
                        .orElse(null);
                
                if (childTransaction != null) {
                    String txnStatusCode = txnData.get("status_code").toString();
                    String txnStatus = txnData.get("status").toString();
                    String childStatus = mapIntasendTransactionStatusToTransactionStatus(txnStatusCode, txnStatus);
                    
                    childTransaction.setStatus(childStatus);
                    childTransaction.setUpdatedAt(LocalDateTime.now());
                    
                    if (txnData.containsKey("charge") && txnData.get("charge") != null) {
                        childTransaction.setFee(new BigDecimal(txnData.get("charge").toString()));
                    }
                    
                    if (txnData.containsKey("provider") && txnData.get("provider") != null) {
                        childTransaction.setProvider(txnData.get("provider").toString());
                    }
                    
                    transactionDao.updateTransaction(childTransaction);
                    
                    log.debug("Child transaction {} updated from polling: {}", intasendTxnId, childStatus);
                }
            }
        }
        
        log.info("Send Money batch {} updated from polling - Status: {}, Children: {}", 
                batchTransaction.getTransactionRef(), newBatchStatus, 
                transactions != null ? transactions.size() : 0);
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
