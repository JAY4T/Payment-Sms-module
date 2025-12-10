package com.module.paymentsms.dao;

import com.module.paymentsms.entity.SmsMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface SmsMessageDao {
    SmsMessage createMessage(SmsMessage smsMessage);
    SmsMessage getSmsMessageById(Long id);
    Page<SmsMessage> getAllSmsMessages(String service /*LIGIOPEN, KAAKAZINI, SOTETULE etc*/, String provider /*CELCOM*/, String sender, String recipient, String status /*DELIVERED, NOT_DELIVERED*/, String celcomMessageId, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Pageable pageable);
}
