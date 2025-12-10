package com.module.paymentsms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class SmsMessageDto {
    private Long id;
    private String service; // LIGIOPEN, KAAKAZINI, SOTETULE etc
    private String provider; // CELCOM etc
    private String sender;
    private String recipient;
    private String message;
    private String status; //DELIVERED, NOT_DELIVERED
    private String celcomMessageId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
