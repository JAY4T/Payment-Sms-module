package com.module.paymentsms.controller;

import com.module.paymentsms.config.BuildResponse;
import com.module.paymentsms.dto.CelcomSenderCreationDto;
import com.module.paymentsms.dto.CelcomSenderDto;
import com.module.paymentsms.dto.CelcomSenderUpdateDto;
import com.module.paymentsms.service.CelcomSenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/celcom-senders")
@Slf4j
public class CelcomSenderControllerImpl implements CelcomSenderController {

    private final BuildResponse buildResponse;
    private final CelcomSenderService celcomSenderService;

    @Autowired
    public CelcomSenderControllerImpl(
            BuildResponse buildResponse,
            CelcomSenderService celcomSenderService
    ) {
        this.buildResponse = buildResponse;
        this.celcomSenderService = celcomSenderService;
    }

    @Override
    @PostMapping
    public ResponseEntity<Object> createCelcomSender(@RequestBody CelcomSenderCreationDto celcomSenderCreationDto) {
        try {
            CelcomSenderDto celcomSender = celcomSenderService.createCelcomSender(celcomSenderCreationDto);
            return buildResponse.success(celcomSender, "Celcom sender registered successfully", null, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Error registering Celcom sender: {}", e.getMessage(), e);
            return buildResponse.error("Failed to register Celcom sender: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @GetMapping
    public ResponseEntity<Object> getAllCelcomSenders() {
        try {
            List<CelcomSenderDto> celcomSenders = celcomSenderService.getAllCelcomSenders();
            return buildResponse.success(celcomSenders, "Celcom senders retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving Celcom senders: {}", e.getMessage(), e);
            return buildResponse.error("Failed to retrieve Celcom senders: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateCelcomSender(@PathVariable Long id, @RequestBody CelcomSenderUpdateDto celcomSenderUpdateDto) {
        try {
            celcomSenderUpdateDto.setId(id);
            CelcomSenderDto celcomSender = celcomSenderService.updateCelcomSender(celcomSenderUpdateDto);
            return buildResponse.success(celcomSender, "Celcom sender updated successfully");
        } catch (Exception e) {
            log.error("Error updating Celcom sender {}: {}", id, e.getMessage(), e);
            return buildResponse.error("Failed to update Celcom sender: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @PostMapping("/{id}/revoke")
    public ResponseEntity<Object> revokeCelcomSender(@PathVariable Long id) {
        try {
            CelcomSenderDto celcomSender = celcomSenderService.revokeCelcomSender(id);
            return buildResponse.success(celcomSender, "Celcom sender revoked successfully");
        } catch (Exception e) {
            log.error("Error revoking Celcom sender {}: {}", id, e.getMessage(), e);
            return buildResponse.error("Failed to revoke Celcom sender: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
