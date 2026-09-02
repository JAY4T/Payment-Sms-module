package com.module.paymentsms.service;

import com.module.paymentsms.dao.ApiClientDao;
import com.module.paymentsms.dto.ApiClientCreationDto;
import com.module.paymentsms.dto.ApiClientDto;
import com.module.paymentsms.entity.ApiClient;
import com.module.paymentsms.mapper.ApiClientDtoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiClientServiceImpl implements ApiClientService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiClientDao apiClientDao;
    private final ApiClientDtoMapper apiClientDtoMapper;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public ApiClientServiceImpl(
            ApiClientDao apiClientDao,
            ApiClientDtoMapper apiClientDtoMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.apiClientDao = apiClientDao;
        this.apiClientDtoMapper = apiClientDtoMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public ApiClientDto createApiClient(ApiClientCreationDto apiClientCreationDto) {
        LocalDateTime now = LocalDateTime.now();

        String apiKey = "kp_" + randomToken(16);
        String apiSecret = randomToken(32);

        ApiClient apiClient = ApiClient.builder()
                .name(apiClientCreationDto.getName())
                .description(apiClientCreationDto.getDescription())
                .apiKey(apiKey)
                .apiSecretHash(passwordEncoder.encode(apiSecret))
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        apiClientDao.createApiClient(apiClient);

        log.info("Created API client '{}' (id={}, apiKey={})", apiClient.getName(), apiClient.getId(), apiKey);

        ApiClientDto dto = apiClientDtoMapper.toApiClientDto(apiClient);
        dto.setApiSecret(apiSecret);
        return dto;
    }

    @Override
    public List<ApiClientDto> getAllApiClients() {
        return apiClientDao.getAllApiClients().stream()
                .map(apiClientDtoMapper::toApiClientDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ApiClientDto revokeApiClient(Long id) {
        ApiClient apiClient = apiClientDao.getApiClientById(id);
        if (apiClient == null) {
            throw new RuntimeException("API client not found with ID: " + id);
        }

        apiClient.setActive(false);
        apiClient.setUpdatedAt(LocalDateTime.now());
        apiClientDao.updateApiClient(apiClient);

        log.info("Revoked API client '{}' (id={})", apiClient.getName(), id);

        return apiClientDtoMapper.toApiClientDto(apiClient);
    }

    @Override
    @Transactional
    public boolean validateCredentials(String apiKey, String apiSecret) {
        if (apiKey == null || apiSecret == null) {
            return false;
        }

        ApiClient apiClient = apiClientDao.getApiClientByApiKey(apiKey);
        if (apiClient == null || !Boolean.TRUE.equals(apiClient.getActive())) {
            return false;
        }

        if (!passwordEncoder.matches(apiSecret, apiClient.getApiSecretHash())) {
            return false;
        }

        apiClient.setLastUsedAt(LocalDateTime.now());
        apiClientDao.updateApiClient(apiClient);

        return true;
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
