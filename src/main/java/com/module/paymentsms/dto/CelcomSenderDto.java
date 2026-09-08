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
public class CelcomSenderDto {
    private Long id;
    private String shortcode;
    private String partnerId;
    private String apiKey;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
