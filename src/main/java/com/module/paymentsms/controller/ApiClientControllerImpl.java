package com.module.paymentsms.controller;

import com.module.paymentsms.config.BuildResponse;
import com.module.paymentsms.dto.ApiClientCreationDto;
import com.module.paymentsms.dto.ApiClientDto;
import com.module.paymentsms.service.ApiClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/api-clients")
@Slf4j
public class ApiClientControllerImpl implements ApiClientController {

    private final BuildResponse buildResponse;
    private final ApiClientService apiClientService;

    @Autowired
    public ApiClientControllerImpl(
            BuildResponse buildResponse,
            ApiClientService apiClientService
    ) {
        this.buildResponse = buildResponse;
        this.apiClientService = apiClientService;
    }

    @Override
    @PostMapping
    public ResponseEntity<Object> createApiClient(@RequestBody ApiClientCreationDto apiClientCreationDto) {
        try {
            ApiClientDto apiClient = apiClientService.createApiClient(apiClientCreationDto);
            return buildResponse.success(apiClient,
                    "API client created successfully. Store the apiSecret now - it will not be shown again.",
                    null, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Error creating API client: {}", e.getMessage(), e);
            return buildResponse.error("Failed to create API client: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @GetMapping
    public ResponseEntity<Object> getAllApiClients() {
        try {
            List<ApiClientDto> apiClients = apiClientService.getAllApiClients();
            return buildResponse.success(apiClients, "API clients retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving API clients: {}", e.getMessage(), e);
            return buildResponse.error("Failed to retrieve API clients: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @PostMapping("/{id}/revoke")
    public ResponseEntity<Object> revokeApiClient(@PathVariable Long id) {
        try {
            ApiClientDto apiClient = apiClientService.revokeApiClient(id);
            return buildResponse.success(apiClient, "API client revoked successfully");
        } catch (Exception e) {
            log.error("Error revoking API client {}: {}", id, e.getMessage(), e);
            return buildResponse.error("Failed to revoke API client: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
