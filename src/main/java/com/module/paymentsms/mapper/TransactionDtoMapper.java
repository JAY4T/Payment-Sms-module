package com.module.paymentsms.mapper;

import com.module.paymentsms.dto.TransactionDto;
import com.module.paymentsms.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionDtoMapper {

    public TransactionDto toTransactionDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .reference(transaction.getTransactionRef())
                .provider(transaction.getProvider())
                .sender(transaction.getSender())
                .currency(transaction.getCurrency())
                .amount(transaction.getAmount() != null ? String.valueOf(transaction.getAmount()) : null)
                .fee(transaction.getFee() != null ? String.valueOf(transaction.getFee()) : null)
                .method(transaction.getMethod())
                .status(transaction.getStatus())
                .type(transaction.getType())
                .narration(transaction.getNarration())
                .failureReason(transaction.getFailureReason())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}
