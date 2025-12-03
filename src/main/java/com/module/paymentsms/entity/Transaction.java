package com.module.paymentsms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_ref", unique = true)
    private String transactionRef;

    @Column(name = "invoice_id")
    private String invoiceId;

    private String method; // MOBILE_WALLET, BANK_TRANSFER, CARD_PAYMENT

    private String type; // CREDIT, DEBIT

    private BigDecimal amount;

    private BigDecimal fee;

    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(columnDefinition = "TEXT")
    private String narration;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TransactionCallback> callbacks;
}
