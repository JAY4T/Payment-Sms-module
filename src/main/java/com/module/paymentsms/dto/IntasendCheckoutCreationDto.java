package com.module.paymentsms.dto;

import com.module.paymentsms.entity.TransactionMethod;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class IntasendCheckoutCreationDto {
    private BigDecimal amount;
    private String currency;
    private TransactionMethod method; // INTASEND_MPESA_STK, INTASEND_CHECKOUT_LINK
    private String phoneNumber;
    private Long walletId;
    private String notes; // Reason
    private String redirectUrl; // Where Intasend's hosted checkout page sends the browser after payment - INTASEND_CHECKOUT_LINK only, ignored for STK push
}
