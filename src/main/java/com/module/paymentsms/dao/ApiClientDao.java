package com.module.paymentsms.dao;

import com.module.paymentsms.entity.ApiClient;

import java.util.List;

public interface ApiClientDao {
    ApiClient createApiClient(ApiClient apiClient);
    ApiClient updateApiClient(ApiClient apiClient);
    ApiClient getApiClientById(Long id);
    ApiClient getApiClientByApiKey(String apiKey);
    List<ApiClient> getAllApiClients();
}
