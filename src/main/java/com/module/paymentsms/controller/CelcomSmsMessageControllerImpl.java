package com.module.paymentsms.controller;

import com.module.paymentsms.config.BuildResponse;
import com.module.paymentsms.dto.PaginationDto;
import com.module.paymentsms.dto.SmsCreationDto;
import com.module.paymentsms.dto.SmsMessageDto;
import com.module.paymentsms.service.CelcomSmsMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/sms")
@Slf4j
public class CelcomSmsMessageControllerImpl implements CelcomSmsMessageController {
    private final BuildResponse buildResponse;
    private final CelcomSmsMessageService celcomSmsMessageService;

    @Autowired
    public CelcomSmsMessageControllerImpl(
            BuildResponse buildResponse,
            CelcomSmsMessageService celcomSmsMessageService
    ) {
        this.buildResponse = buildResponse;
        this.celcomSmsMessageService = celcomSmsMessageService;
    }

    @Override
    @PostMapping("/send")
    public ResponseEntity<Object> sendMessage(@RequestBody SmsCreationDto smsCreationDto) {
        try {
            log.info("Sending SMS: {}", smsCreationDto);
            SmsMessageDto smsMessage = celcomSmsMessageService.sendMessage(smsCreationDto);
            return buildResponse.success(smsMessage, "SMS sent successfully", null, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Error sending SMS: {}", e.getMessage(), e);
            return buildResponse.error("Failed to send SMS: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<Object> getMessageById(@PathVariable Long id) {
        try {
            log.info("Retrieving SMS message by ID: {}", id);
            SmsMessageDto smsMessage = celcomSmsMessageService.getMessageById(id);
            return buildResponse.success(smsMessage, "SMS message retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving SMS message by ID {}: {}", id, e.getMessage(), e);
            return buildResponse.error("Failed to retrieve SMS message: " + e.getMessage(), null, HttpStatus.NOT_FOUND);
        }
    }

    @Override
    @GetMapping
    public ResponseEntity<Object> getAllSmsMessages(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String celcomMessageId,
            @RequestParam(required = false) LocalDate createdAtStartDate,
            @RequestParam(required = false) LocalDate createdAtEndDate,
            @RequestParam(required = false) LocalDate updatedAtStartDate,
            @RequestParam(required = false) LocalDate updatedAtEndDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        try {
            // Validate pagination parameters (1-based pagination)
            if (page < 1) {
                return buildResponse.error("Page number must be greater than or equal to 1", null, HttpStatus.BAD_REQUEST);
            }
            if (size < 1) {
                return buildResponse.error("Page size must be greater than or equal to 1", null, HttpStatus.BAD_REQUEST);
            }

            log.info("Retrieving SMS messages with filters - service: {}, provider: {}, status: {}, page: {}, size: {}",
                    service, provider, status, page, size);

            // Convert LocalDate to LocalDateTime (start of day and end of day)
            LocalDateTime createdAtStart = createdAtStartDate != null ? createdAtStartDate.atStartOfDay() : null;
            LocalDateTime createdAtEnd = createdAtEndDate != null ? createdAtEndDate.atTime(23, 59, 59) : null;
            LocalDateTime updatedAtStart = updatedAtStartDate != null ? updatedAtStartDate.atStartOfDay() : null;
            LocalDateTime updatedAtEnd = updatedAtEndDate != null ? updatedAtEndDate.atTime(23, 59, 59) : null;

            PaginationDto<SmsMessageDto> smsMessages = celcomSmsMessageService.getAllSmsMessages(
                    service, provider, sender, recipient, status, celcomMessageId,
                    createdAtStart, createdAtEnd,
                    updatedAtStart, updatedAtEnd,
                    page, size
            );
            return buildResponse.success(smsMessages, "SMS messages retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving SMS messages: {}", e.getMessage(), e);
            return buildResponse.error("Failed to retrieve SMS messages: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


}
