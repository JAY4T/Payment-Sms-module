package com.module.paymentsms.mapper;

import com.module.paymentsms.dto.CelcomSenderDto;
import com.module.paymentsms.entity.CelcomSender;
import org.springframework.stereotype.Component;

@Component
public class CelcomSenderDtoMapper {

    public CelcomSenderDto toCelcomSenderDto(CelcomSender celcomSender) {
        return CelcomSenderDto.builder()
                .id(celcomSender.getId())
                .shortcode(celcomSender.getShortcode())
                .partnerId(celcomSender.getPartnerId())
                .apiKey(celcomSender.getApiKey())
                .active(celcomSender.getActive())
                .createdAt(celcomSender.getCreatedAt())
                .updatedAt(celcomSender.getUpdatedAt())
                .build();
    }
}
