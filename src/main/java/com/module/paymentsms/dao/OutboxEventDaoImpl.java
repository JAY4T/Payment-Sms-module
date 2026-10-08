package com.module.paymentsms.dao;

import com.module.paymentsms.entity.OutboxEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class OutboxEventDaoImpl implements OutboxEventDao {

    private final EntityManager entityManager;

    @Autowired
    public OutboxEventDaoImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insertIfAbsent(OutboxEvent event) {
        entityManager.createNativeQuery(
                        "INSERT INTO outbox_events " +
                                "(event_id, exchange_name, routing_key, payload, status, attempts, created_at, next_attempt_at) " +
                                "VALUES (:eventId, :exchangeName, :routingKey, :payload, :status, 0, :createdAt, :nextAttemptAt) " +
                                "ON CONFLICT (event_id) DO NOTHING")
                .setParameter("eventId", event.getEventId())
                .setParameter("exchangeName", event.getExchangeName())
                .setParameter("routingKey", event.getRoutingKey())
                .setParameter("payload", event.getPayload())
                .setParameter("status", OutboxEvent.STATUS_PENDING)
                .setParameter("createdAt", event.getCreatedAt())
                .setParameter("nextAttemptAt", event.getNextAttemptAt())
                .executeUpdate();
    }

    @Override
    public List<OutboxEvent> findDueForPublish(LocalDateTime now, int limit) {
        TypedQuery<OutboxEvent> query = entityManager.createQuery(
                "SELECT e FROM OutboxEvent e " +
                        "WHERE e.status IN :statuses AND e.nextAttemptAt <= :now " +
                        "ORDER BY e.createdAt ASC",
                OutboxEvent.class);
        query.setParameter("statuses", List.of(OutboxEvent.STATUS_PENDING, OutboxEvent.STATUS_FAILED));
        query.setParameter("now", now);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @Override
    public OutboxEvent markPublished(Long id) {
        OutboxEvent event = entityManager.find(OutboxEvent.class, id);
        if (event == null) {
            return null;
        }
        event.setStatus(OutboxEvent.STATUS_PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        event.setLastError(null);
        return entityManager.merge(event);
    }

    @Override
    public OutboxEvent recordFailure(Long id, String error, LocalDateTime nextAttemptAt) {
        OutboxEvent event = entityManager.find(OutboxEvent.class, id);
        if (event == null) {
            return null;
        }
        event.setStatus(OutboxEvent.STATUS_FAILED);
        event.setAttempts(event.getAttempts() == null ? 1 : event.getAttempts() + 1);
        // Truncated: a provider stack trace is not worth an unbounded TEXT column, and the count is
        // what alerting acts on.
        event.setLastError(error == null ? null : error.substring(0, Math.min(error.length(), 2000)));
        event.setNextAttemptAt(nextAttemptAt);
        return entityManager.merge(event);
    }

    @Override
    public LocalDateTime findOldestUnpublishedCreatedAt() {
        try {
            return entityManager.createQuery(
                            "SELECT MIN(e.createdAt) FROM OutboxEvent e WHERE e.status <> :published",
                            LocalDateTime.class)
                    .setParameter("published", OutboxEvent.STATUS_PUBLISHED)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
