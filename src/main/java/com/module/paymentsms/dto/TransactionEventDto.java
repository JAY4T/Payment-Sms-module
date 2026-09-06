package com.module.paymentsms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

// Payload published to RabbitMQ (RabbitConfig.TRANSACTIONS_EXCHANGE) for transaction.completed,
// transaction.failed, and transaction.cleared. Deliberately smaller than TransactionDto - just
// enough for a consumer to act on the event without a follow-up call.
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class TransactionEventDto {
    private Long id;
    private String reference;
    private Long walletId;
    private String amount;
    private String currency;
    private String fee;
    private String status;
    private String narration;
}
