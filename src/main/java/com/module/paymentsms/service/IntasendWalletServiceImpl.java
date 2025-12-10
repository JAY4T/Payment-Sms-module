package com.module.paymentsms.service;

import com.google.gson.Gson;
import com.module.paymentsms.dao.WalletDao;
import com.module.paymentsms.dto.PaginationDto;
import com.module.paymentsms.dto.WalletCreationDto;
import com.module.paymentsms.dto.WalletDto;
import com.module.paymentsms.dto.WalletUpdateDto;
import com.module.paymentsms.entity.Wallet;
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

    @Value("${intasend.secret.key}")
    private String intasendSecretKey;

    @Value("${intasend.public.key}")
    private String intasendPublicKey;

    @Value("${intasend.wallet.url}")
    private String walletUrl;

    @Autowired
    public IntasendWalletServiceImpl(
            WalletDao walletDao,
            WalletDtoMapper walletDtoMapper
    ) {
        this.walletDao = walletDao;
        this.walletDtoMapper = walletDtoMapper;
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
