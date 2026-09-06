package com.module.paymentsms.mapper;

import com.module.paymentsms.dto.ApiClientDto;
import com.module.paymentsms.entity.ApiClient;
import org.springframework.stereotype.Component;

@Component
public class ApiClientDtoMapper {

    public ApiClientDto toApiClientDto(ApiClient apiClient) {
        return ApiClientDto.builder()
                .id(apiClient.getId())
                .name(apiClient.getName())
                .description(apiClient.getDescription())
                .apiKey(apiClient.getApiKey())
                .active(apiClient.getActive())
                .createdAt(apiClient.getCreatedAt())
                .updatedAt(apiClient.getUpdatedAt())
                .lastUsedAt(apiClient.getLastUsedAt())
                .build();
    }
}
