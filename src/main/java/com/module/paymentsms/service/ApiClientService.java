package com.module.paymentsms.service;

import com.module.paymentsms.dto.ApiClientCreationDto;
import com.module.paymentsms.dto.ApiClientDto;

import java.util.List;

public interface ApiClientService {
    ApiClientDto createApiClient(ApiClientCreationDto apiClientCreationDto);
    List<ApiClientDto> getAllApiClients();
    ApiClientDto revokeApiClient(Long id);
    boolean validateCredentials(String apiKey, String apiSecret);
}
