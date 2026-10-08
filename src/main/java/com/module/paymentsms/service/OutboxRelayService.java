package com.module.paymentsms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.module.paymentsms.dao.OutboxEventDao;
import com.module.paymentsms.entity.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Publishes the {@link OutboxEvent} backlog to RabbitMQ, with retries.
 *
 * <p>Runs on a short interval and does the publishing outside the producing callers' transactions,
 * so a broker that is down or slow can no longer affect payment processing itself - it only delays
 * the notification. Each event keeps its {@code eventId} across every retry, which is what lets a
 * consumer deduplicate.
 */
@Service
@Slf4j
public class OutboxRelayService {

    private static final int BATCH_SIZE = 200;

    // 30s, 60s, 2m, 4m, 8m, capped at 15m. Long enough not to hammer a broker that is down, short
    // enough that a transient blip is repaired well within a human's attention span.
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(15);

    // A backlog this old is not a blip: it means the broker has been unreachable or the relay
    // isn't running, and every one of those events is money that LigiOpen hasn't been told about.
    private static final Duration BACKLOG_ALERT_AFTER = Duration.ofMinutes(5);

    private final OutboxEventDao outboxEventDao;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${outbox.relay.enabled:true}")
    private boolean enabled = true;

    public OutboxRelayService(OutboxEventDao outboxEventDao, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.outboxEventDao = outboxEventDao;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval:15000}")
    @Transactional
    public void relayPendingEvents() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> due;
        try {
            due = outboxEventDao.findDueForPublish(now, BATCH_SIZE);
        } catch (Exception e) {
            log.error("Outbox relay could not read the backlog: {}", e.getMessage(), e);
            return;
        }

        for (OutboxEvent event : due) {
            try {
                rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(),
                        objectMapper.readValue(event.getPayload(), Object.class));
                outboxEventDao.markPublished(event.getId());
                log.debug("Outbox relay published {} ({})", event.getRoutingKey(), event.getEventId());
            } catch (Exception e) {
                LocalDateTime nextAttemptAt = now.plus(backoffFor(event.getAttempts()));
                outboxEventDao.recordFailure(event.getId(), e.getMessage(), nextAttemptAt);
                log.warn("Outbox relay failed to publish {} ({}), attempt {} - retrying at {}: {}",
                        event.getRoutingKey(), event.getEventId(),
                        (event.getAttempts() == null ? 0 : event.getAttempts()) + 1,
                        nextAttemptAt, e.getMessage());
            }
        }

        alertIfBacklogIsStale(now);
    }

    private Duration backoffFor(Integer attempts) {
        int attempt = attempts == null ? 0 : attempts;
        Duration backoff = BASE_BACKOFF.multipliedBy(1L << Math.min(attempt, 5));
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    private void alertIfBacklogIsStale(LocalDateTime now) {
        try {
            LocalDateTime oldest = outboxEventDao.findOldestUnpublishedCreatedAt();
            if (oldest == null) {
                return;
            }
            Duration age = Duration.between(oldest, now);
            if (age.compareTo(BACKLOG_ALERT_AFTER) > 0) {
                log.error("OUTBOX BACKLOG ALERT: unpublished transaction events are {} minutes old " +
                                "(oldest {}). RabbitMQ delivery has been failing long enough that " +
                                "downstream settlement cannot be trusted to have happened - check the " +
                                "broker and the relay, and reconcile affected settlements manually.",
                        age.toMinutes(), oldest);
            }
        } catch (Exception e) {
            log.error("Outbox backlog age check failed: {}", e.getMessage(), e);
        }
    }
}
