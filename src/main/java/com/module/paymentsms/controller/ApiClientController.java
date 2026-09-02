package com.module.paymentsms.controller;

import com.module.paymentsms.dto.ApiClientCreationDto;
import org.springframework.http.ResponseEntity;

public interface ApiClientController {
    ResponseEntity<Object> createApiClient(ApiClientCreationDto apiClientCreationDto);
    ResponseEntity<Object> getAllApiClients();
    ResponseEntity<Object> revokeApiClient(Long id);
}
