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

    @Column(name = "intasend_tracking_id")
    private String intasendTrackingId;
    
    @Column(name = "intasend_transaction_id")
    private String intasendTransactionId;

    private String provider;

    private String sender;

    @Enumerated(EnumType.STRING)
    private TransactionMethod method;

    private String type;

    private String currency;

    private BigDecimal amount;

    private BigDecimal fee;

    private String status;

    @Column(columnDefinition = "TEXT")
    private String narration;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(name = "has_batch")
    private Boolean hasBatch;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TransactionCallback> callbacks;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TransactionMetaData> transactionMetaData;
    
    @OneToMany(mappedBy = "parentTransaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Transaction> batchTransactions;
    
    @ManyToOne
    @JoinColumn(name = "parent_transaction_id")
    private Transaction parentTransaction;

    @ManyToOne
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @Column(name = "checkout_link")
    private String checkoutLink;

    @Column(name = "clearing_status")
    private String clearingStatus;
}
