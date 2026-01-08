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
public class IntasendMpesaBTBTillDto {
    private String currency;
    private Long walletId;
    private Boolean requiresApproval;
    private List<MpesaBTBTillTransactionDto> transactions;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MpesaBTBTillTransactionDto {
        private BigDecimal amount;
        private String tillNumber;
        private String narration;
    }
}
