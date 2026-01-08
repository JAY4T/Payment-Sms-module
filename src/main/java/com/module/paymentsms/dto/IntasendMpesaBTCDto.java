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
public class IntasendMpesaBTCDto {
    private String currency;
    private Long walletId;
    private List<MpesaBTCTransactionDto> transactions;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MpesaBTCTransactionDto {
        private BigDecimal amount;
        private String recipientPhoneNumber;
        private String narration;
    }
}
