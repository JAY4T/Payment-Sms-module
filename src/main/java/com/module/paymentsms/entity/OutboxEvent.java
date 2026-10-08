package com.module.paymentsms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A transaction lifecycle event that must reach LigiOpen, written to the database in the same
 * transaction as the state change that produced it.
 *
 * <p>Events used to be published with a fire-and-forget {@code rabbitTemplate.convertAndSend} inside
 * a try/catch that logged and swallowed the failure. If the broker was unreachable, or the exchange
 * hadn't been declared yet, or the process died between committing "cleared" and publishing it, the
 * event was simply gone - and the only thing on the other side that could have moved a seller's or
 * club's money was that event. Nothing retried, and nothing recorded that it had been dropped.
 *
 * <p>The outbox removes the broker from the critical path: the event is a row, committed
 * atomically with the status change, and a relay publishes it (with retries) afterwards. An event
 * can now be delayed but not lost.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Consumer-facing idempotency key: stable across every retry of the same logical event, so a
     * consumer that has already processed it can recognise and skip a duplicate. This is what makes
     * at-least-once delivery safe.
     */
    @Column(name = "event_id", unique = true, nullable = false, length = 120)
    private String eventId;

    @Column(name = "exchange_name", nullable = false, length = 120)
    private String exchangeName;

    @Column(name = "routing_key", nullable = false, length = 120)
    private String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
