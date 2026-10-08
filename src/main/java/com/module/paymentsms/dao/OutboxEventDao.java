package com.module.paymentsms.dao;

import com.module.paymentsms.entity.OutboxEvent;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventDao {

    /**
     * Enqueues an event, doing nothing if an event with the same {@code eventId} already exists.
     *
     * <p>Deliberately a single {@code INSERT ... ON CONFLICT DO NOTHING} rather than a
     * check-then-insert: the producers of these events are provider callbacks that get retried, so
     * two of them can race on the same logical event. A plain insert would throw a constraint
     * violation inside the caller's transaction and, depending on the flush timing, poison it - the
     * callback would fail for no good reason. This form is atomic and silent.
     */
    void insertIfAbsent(OutboxEvent event);

    /**
     * The batch a relay should try to publish now: PENDING (or FAILED but retryable) rows whose
     * next attempt is due, oldest first.
     */
    List<OutboxEvent> findDueForPublish(LocalDateTime now, int limit);

    OutboxEvent markPublished(Long id);

    /** Records a failed attempt and schedules the next one at {@code nextAttemptAt}. */
    OutboxEvent recordFailure(Long id, String error, LocalDateTime nextAttemptAt);

    /** Age of the oldest event that still hasn't been published, or null if the outbox is empty. */
    LocalDateTime findOldestUnpublishedCreatedAt();
}
