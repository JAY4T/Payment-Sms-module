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
@Table(name = "celcom_senders")
public class CelcomSender {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Matches the "sender" field on an SMS request (e.g. "LigiOpen", "KaaKazini") - this is
    // also the exact value sent to Celcom as "shortcode".
    @Column(name = "shortcode", unique = true)
    private String shortcode;

    @Column(name = "partner_id")
    private String partnerId;

    @Column(name = "api_key")
    private String apiKey;

    private Boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
