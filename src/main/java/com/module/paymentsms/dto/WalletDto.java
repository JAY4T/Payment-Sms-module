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
public class WalletDto {
    private Long id;
    private String name;
    private String description;
    private String balance;
    private String availableBalance;
    private Boolean isSystemWallet;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
