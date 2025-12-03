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
public class TransactionDto {
    private Long id;
    private String reference;
    private String provider;
    private String sender;
    private String currency;
    private String amount;
    private String fee;
    private String method;
    private String status;
    private String type;
    private String narration;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
