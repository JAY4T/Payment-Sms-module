package com.module.paymentsms.controller;

import com.module.paymentsms.dto.SmsCreationDto;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface CelcomSmsMessageController {
    ResponseEntity<Object> sendMessage(SmsCreationDto smsCreationDto);
    ResponseEntity<Object> getMessageById(Long id);
    ResponseEntity<Object> getAllSmsMessages(String service /*LIGIOPEN, KAAKAZINI, SOTETULE etc*/, String provider /*CELCOM*/, String sender, String recipient, String status /*DELIVERED, NOT_DELIVERED*/, String celcomMessageId, LocalDate createdAtStartDate, LocalDate createdAtEndDate, LocalDate updatedAtStartDate, LocalDate updatedAtEndDate, Integer page, Integer size);
}
