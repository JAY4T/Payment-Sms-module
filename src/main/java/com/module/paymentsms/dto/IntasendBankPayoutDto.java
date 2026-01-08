package com.module.paymentsms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class IntasendBankPayoutDto {
    private String currency;
    private Long walletId;
    private String provider;
    private Boolean requiresApproval;
    private List<IntasendBankPayoutTransactionDto> transactions;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IntasendBankPayoutTransactionDto {
        private BigDecimal amount;
        private String bankCode;
        private String account;
        private String narration;
    }
}
