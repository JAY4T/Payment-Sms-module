package com.module.paymentsms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

// Source wallet is the {id} path variable on the transfer endpoint - this only carries the
// destination and transfer details, matching Intasend's own intra_transfer request shape.
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class WalletTransferRequestDto {
    private String toIntasendWalletId;
    private BigDecimal amount;
    private String narrative;
}
