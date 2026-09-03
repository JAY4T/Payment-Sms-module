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

    // Optional: lets the caller's own reference (e.g. mledger's transactionRef) become this
    // transaction's actual reference/api_ref, instead of kiwipay generating its own. Must be
    // globally unique (enforced by transactions.transaction_ref) - the caller's problem if it
    // collides. Omit to get the usual auto-generated <timestamp>_<uuid>_MAG reference.
    private String reference;
}
