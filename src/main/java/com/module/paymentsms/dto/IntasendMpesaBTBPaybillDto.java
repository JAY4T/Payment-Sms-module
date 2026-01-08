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
public class IntasendMpesaBTBPaybillDto {
    private String currency;
    private Long walletId;
    private Boolean requiresApproval;
    private List<MpesaBTBPaybillTransactionDto> transactions;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MpesaBTBPaybillTransactionDto {
        private BigDecimal amount;
        private String paybillNumber;
        private String accountReference;
        private String narration;
    }
}
