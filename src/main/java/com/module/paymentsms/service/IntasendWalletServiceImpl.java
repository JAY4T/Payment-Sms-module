package com.module.paymentsms.service;

import com.google.gson.Gson;
import com.module.paymentsms.dao.TransactionDao;
import com.module.paymentsms.dao.WalletDao;
import com.module.paymentsms.dto.PaginationDto;
import com.module.paymentsms.dto.TransactionDto;
import com.module.paymentsms.dto.WalletCreationDto;
import com.module.paymentsms.dto.WalletDto;
import com.module.paymentsms.dto.WalletTransferRequestDto;
import com.module.paymentsms.dto.WalletUpdateDto;
import com.module.paymentsms.entity.Transaction;
import com.module.paymentsms.entity.TransactionMethod;
import com.module.paymentsms.entity.Wallet;
import com.module.paymentsms.mapper.TransactionDtoMapper;
import com.module.paymentsms.mapper.WalletDtoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IntasendWalletServiceImpl implements IntasendWalletService {
    private final WalletDao walletDao;
    private final WalletDtoMapper walletDtoMapper;
    private final TransactionDao transactionDao;
    private final TransactionDtoMapper transactionDtoMapper;

    @Value("${intasend.secret.key}")
    private String intasendSecretKey;

    @Value("${intasend.public.key}")
    private String intasendPublicKey;

    @Value("${intasend.wallet.url}")
    private String walletUrl;

    @Autowired
    public IntasendWalletServiceImpl(
            WalletDao walletDao,
            WalletDtoMapper walletDtoMapper,
            TransactionDao transactionDao,
            TransactionDtoMapper transactionDtoMapper
    ) {
        this.walletDao = walletDao;
        this.walletDtoMapper = walletDtoMapper;
        this.transactionDao = transactionDao;
        this.transactionDtoMapper = transactionDtoMapper;
    }

    @Transactional
    @Override
    public WalletDto createWallet(WalletCreationDto walletCreationDto) {
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> walletDetails = createIntasendWallet(walletCreationDto.getIsSystemWallet());

        String intasendWalletId = walletDetails.get("wallet_id").toString();

        Wallet wallet = Wallet.builder()
                .intasendWalletId(intasendWalletId)
                .name(walletCreationDto.getName())
                .description(walletCreationDto.getDescription())
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .isSystemWallet(walletCreationDto.getIsSystemWallet())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return walletDtoMapper.toWalletDto(walletDao.createWallet(wallet));
    }

    @Transactional
    @Override
    public WalletDto updateWallet(WalletUpdateDto walletUpdateDto) {
        LocalDateTime now = LocalDateTime.now();

        Wallet wallet = walletDao.getWalletById(walletUpdateDto.getId());
        if (wallet == null) {
            throw new RuntimeException("Wallet not found with ID: " + walletUpdateDto.getId());
        }

        wallet.setName(walletUpdateDto.getName());
        wallet.setDescription(walletUpdateDto.getDescription());
        wallet.setUpdatedAt(now);

        return walletDtoMapper.toWalletDto(walletDao.updateWallet(wallet));
    }

    @Override
    public WalletDto getWalletById(Long id) {
        Wallet wallet = walletDao.getWalletById(id);
        if (wallet == null) {
            throw new RuntimeException("Wallet not found with ID: " + id);
        }
        return walletDtoMapper.toWalletDto(wallet);
    }

    @Override
    public WalletDto getWalletByIntasendWalletId(String intasendWalletId) {
        Wallet wallet = walletDao.getWalletByIntasendWalletId(intasendWalletId);
        if (wallet == null) {
            throw new RuntimeException("Wallet not found with Intasend wallet ID: " + intasendWalletId);
        }
        return walletDtoMapper.toWalletDto(wallet);
    }

    @Override
    public PaginationDto<WalletDto> getAllWallets(String name, Boolean isSystemWallet, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Integer page, Integer size) {
        try {
            // Create Pageable object
            Pageable pageable = PageRequest.of(page, size);

            // Get paginated wallets from DAO
            Page<Wallet> walletPage = walletDao.getAllWallets(name, isSystemWallet, createdAtStartDate, createdAtEndDate, updatedAtStartDate, updatedAtEndDate, pageable);

            // Convert to DTOs
            List<WalletDto> walletDtos = walletPage.getContent()
                    .stream()
                    .map(walletDtoMapper::toWalletDto)
                    .collect(Collectors.toList());

            // Create pagination response
            return new PaginationDto<>(
                    walletDtos,
                    page,
                    size,
                    walletPage.getTotalElements()
            );

        } catch (Exception e) {
            log.error("Failed to get wallets: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve wallets", e);
        }
    }

    @Transactional
    @Override
    public WalletDto syncWallet(Long id) {
        Wallet wallet = walletDao.getWalletById(id);
        if (wallet == null) {
            throw new RuntimeException("Wallet not found with ID: " + id);
        }

        Map<String, Object> remoteWallet = fetchIntasendWallet(wallet.getIntasendWalletId());

        wallet.setBalance(new BigDecimal(remoteWallet.get("current_balance").toString()));
        wallet.setAvailableBalance(new BigDecimal(remoteWallet.get("available_balance").toString()));
        wallet.setUpdatedAt(LocalDateTime.now());

        return walletDtoMapper.toWalletDto(walletDao.updateWallet(wallet));
    }

    @Transactional
    @Override
    public TransactionDto transferBetweenWallets(String fromIntasendWalletId, WalletTransferRequestDto request) {
        Wallet fromWallet = walletDao.getWalletByIntasendWalletId(fromIntasendWalletId);
        if (fromWallet == null) {
            throw new RuntimeException("Wallet not found with Intasend wallet ID: " + fromIntasendWalletId);
        }
        // Destination must also be a wallet this service already knows about - an unrecognized
        // Intasend wallet id here is almost certainly a caller-side mistake (wrong id, wrong
        // environment), not a wallet Intasend would actually accept as a valid transfer target.
        Wallet toWallet = walletDao.getWalletByIntasendWalletId(request.getToIntasendWalletId());
        if (toWallet == null) {
            throw new RuntimeException("Wallet not found with Intasend wallet ID: " + request.getToIntasendWalletId());
        }

        LocalDateTime now = LocalDateTime.now();
        Transaction transaction = Transaction.builder()
                .transactionRef("TRANSFER_" + UUID.randomUUID())
                .sender(fromWallet.getName())
                .method(TransactionMethod.INTASEND_WALLET_TRANSFER)
                .type("DEBIT")
                .currency("KES")
                .amount(request.getAmount())
                .status("PENDING")
                .narration(request.getNarrative())
                .createdAt(now)
                .updatedAt(now)
                .callbacks(new ArrayList<>())
                .wallet(fromWallet)
                .build();
        transaction = transactionDao.createTransaction(transaction);

        try {
            Map<String, Object> result = callIntraTransfer(fromIntasendWalletId, request);

            fromWallet.setBalance(new BigDecimal(result.get("current_balance").toString()));
            fromWallet.setAvailableBalance(new BigDecimal(result.get("available_balance").toString()));
            fromWallet.setUpdatedAt(now);
            walletDao.updateWallet(fromWallet);

            transaction.setStatus("COMPLETED");
            transaction.setUpdatedAt(LocalDateTime.now());
            transaction = transactionDao.updateTransaction(transaction);

            return transactionDtoMapper.toTransactionDto(transaction);
        } catch (Exception e) {
            transaction.setStatus("FAILED");
            transaction.setFailureReason(e.getMessage());
            transaction.setUpdatedAt(LocalDateTime.now());
            transactionDao.updateTransaction(transaction);
            log.error("Wallet transfer failed: from={} to={} amount={}: {}",
                    fromIntasendWalletId, request.getToIntasendWalletId(), request.getAmount(), e.getMessage(), e);
            throw new RuntimeException("Failed to transfer between wallets: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> callIntraTransfer(String fromIntasendWalletId, WalletTransferRequestDto request) {
        Gson gson = new Gson();
        String base = walletUrl.endsWith("/") ? walletUrl.substring(0, walletUrl.length() - 1) : walletUrl;
        String transferUrl = base + "/" + fromIntasendWalletId + "/intra_transfer/";

        Map<String, Object> requestBody = Map.of(
                "wallet_id", request.getToIntasendWalletId(),
                "amount", request.getAmount().toPlainString(),
                "narrative", request.getNarrative()
        );

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(new URI(transferUrl))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + intasendSecretKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HttpStatus.OK.value() && response.statusCode() != HttpStatus.CREATED.value()) {
                log.error("Failed to transfer between Intasend wallets. Status: {}, Response: {}", response.statusCode(), response.body());
                throw new RuntimeException("Intasend intra_transfer failed: Status " + response.statusCode() + " - " + response.body());
            }

            Map<String, Object> responseMap = gson.fromJson(response.body(), Map.class);
            if (responseMap.get("current_balance") == null || responseMap.get("available_balance") == null) {
                log.error("Unexpected Intasend intra_transfer response, missing balance fields: {}", response.body());
                throw new RuntimeException("Intasend intra_transfer response missing balance fields");
            }
            return responseMap;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Intasend intra_transfer", e);
        }
    }

    private Map<String, Object> fetchIntasendWallet(String intasendWalletId) {
        Gson gson = new Gson();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(walletUrl + "?record_id=" + intasendWalletId))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + intasendSecretKey)
                    .GET()
                    .build();

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HttpStatus.OK.value()) {
                log.error("Failed to fetch Intasend wallet {}. Status: {}, Response: {}", intasendWalletId, response.statusCode(), response.body());
                throw new RuntimeException("Failed to fetch Intasend wallet: Status " + response.statusCode());
            }

            Map<String, Object> responseMap = gson.fromJson(response.body(), Map.class);
            List<Map<String, Object>> results = (List<Map<String, Object>>) responseMap.get("results");

            if (results == null || results.isEmpty()) {
                log.error("No wallet found on Intasend for record_id: {}", intasendWalletId);
                throw new RuntimeException("Wallet not found on Intasend: " + intasendWalletId);
            }

            log.debug("Fetched Intasend wallet {}: {}", intasendWalletId, results.get(0));
            return results.get(0);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching Intasend wallet {}: {}", intasendWalletId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch Intasend wallet", e);
        }
    }

    Map<String, Object> createIntasendWallet(Boolean isUserWallet) {

        String walletLabel = UUID.randomUUID().toString();
        String cleanLabel = sanitizeLabel(walletLabel);


        Map<String, Object> walletMap = new HashMap<>();

        try {
            walletMap.put("label", cleanLabel);

            // Create request body
            Map<String, Object> requestBody = Map.of(
                    "currency", "KES",
                    "wallet_type", "WORKING",
                    "can_disburse", true,
                    "label", cleanLabel
            );

            Gson gson = new Gson();
            String jsonBody = gson.toJson(requestBody);

            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(walletUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + intasendSecretKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            // Send request
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HttpStatus.CREATED.value() && response.statusCode() != HttpStatus.OK.value()) {
                log.error("Failed to create Intasend wallet. Status: {}, Response: {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to create Intasend wallet: Status " + response.statusCode());
            }

            // Parse response and extract wallet_id
            Map<String, Object> responseMap = gson.fromJson(response.body(), Map.class);
            String walletId = (String) responseMap.get("wallet_id");

            if (walletId == null || walletId.isEmpty()) {
                log.error("No wallet_id found in Intasend response: {}", response.body());
                throw new RuntimeException("No wallet_id returned from Intasend");
            }

            log.debug("Successfully created Intasend wallet with ID: {}", walletId);
            walletMap.put("wallet_id", walletId);
            return walletMap;

        } catch (Exception e) {
            log.error("Error creating Intasend wallet: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Intasend wallet", e);
        }
    }

    private String sanitizeLabel(String label) {
        if (label == null) {
            return "wallet_" + System.currentTimeMillis();
        }

        // Remove new lines, tabs, commas, fullstops and replace with underscores
        // Keep only numbers, letters, underscores, dashes and spaces
        return label.replaceAll("[\\n\\r\\t,.]", "_")
                .replaceAll("[^a-zA-Z0-9_\\-\\s]", "_")
                .trim();
    }
}
