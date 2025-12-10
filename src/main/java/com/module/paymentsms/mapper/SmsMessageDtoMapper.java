package com.module.paymentsms.mapper;

import com.module.paymentsms.dto.SmsMessageDto;
import com.module.paymentsms.entity.SmsMessage;
import org.springframework.stereotype.Component;

@Component
public class SmsMessageDtoMapper {
    public SmsMessageDto toSmsMessageDto(SmsMessage smsMessage) {
        return SmsMessageDto.builder()
                .id(smsMessage.getId())
                .service(smsMessage.getService())
                .provider(smsMessage.getProvider())
                .sender(smsMessage.getSender())
                .recipient(smsMessage.getRecipient())
                .message(smsMessage.getMessage())
                .status(smsMessage.getStatus())
                .celcomMessageId(smsMessage.getCelcomMessageId())
                .createdAt(smsMessage.getCreatedAt())
                .updatedAt(smsMessage.getUpdatedAt())
                .build();
    }
}
