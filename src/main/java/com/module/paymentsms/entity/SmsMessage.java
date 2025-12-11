package com.module.paymentsms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "sms_messages")
public class SmsMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String service; // LIGIOPEN, KAAKAZINI, SOTETULE etc

    private String provider; // CELCOM etc

    private String sender;

    private String recipient;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String status; //DELIVERED, NOT_DELIVERED

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "celcom_message_id")
    private String celcomMessageId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
