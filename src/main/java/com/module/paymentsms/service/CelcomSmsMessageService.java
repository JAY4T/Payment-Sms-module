package com.module.paymentsms.service;

import com.module.paymentsms.dto.PaginationDto;
import com.module.paymentsms.dto.SmsCreationDto;
import com.module.paymentsms.dto.SmsMessageDto;

import java.time.LocalDateTime;

public interface CelcomSmsMessageService {
    SmsMessageDto sendMessage(SmsCreationDto smsCreationDto);
    SmsMessageDto getMessageById(Long id);
    PaginationDto<SmsMessageDto> getAllSmsMessages(String service /*LIGIOPEN, KAAKAZINI, SOTETULE etc*/, String provider /*CELCOM*/, String sender, String recipient, String status /*DELIVERED, NOT_DELIVERED*/, String celcomMessageId, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Integer page, Integer size);
}
