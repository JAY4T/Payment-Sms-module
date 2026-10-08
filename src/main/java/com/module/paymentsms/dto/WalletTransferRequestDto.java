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

    // Optional caller-supplied dedupe key. Intasend's intra_transfer has no idempotency support of
    // its own, so a caller that retries a transfer after a timeout or a crash would move the money
    // twice. Supplying this key makes the call replay-safe: the key is stored on the transaction as
    // TRANSFER_<key> (a UNIQUE column), so a repeat of the same key returns the original result
    // instead of transferring again, and two callers racing the same key have one of them fail on
    // the constraint before any money moves.
    private String idempotencyKey;
}
