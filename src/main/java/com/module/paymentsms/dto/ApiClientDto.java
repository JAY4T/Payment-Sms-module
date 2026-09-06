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
public class ApiClientDto {
    private Long id;
    private String name;
    private String description;
    private String apiKey;

    // Only ever populated on the create response - the plaintext secret is never stored and
    // can't be retrieved again afterward, only regenerated via revoke + recreate.
    private String apiSecret;

    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;
}
