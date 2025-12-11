package com.module.paymentsms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class CheckoutCreationDto {
    private BigDecimal amount;
    private String currency;
    private String method; // M-PESA, PAYMENT_LINK
    private String phoneNumber;
    private Long walletId;
    private String narration; // Reason
}
