package com.module.paymentsms.dao;

import com.module.paymentsms.entity.SmsMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SmsMessageDaoImpl implements SmsMessageDao{

    private final EntityManager entityManager;

    @Autowired
    public SmsMessageDaoImpl(
            EntityManager entityManager
    ) {
        this.entityManager = entityManager;
    }

    @Override
    public SmsMessage createMessage(SmsMessage smsMessage) {
        entityManager.persist(smsMessage);
        return smsMessage;
    }

    @Override
    public SmsMessage getSmsMessageById(Long id) {
        TypedQuery<SmsMessage> query = entityManager.createQuery("from SmsMessage where id = :id", SmsMessage.class);
        query.setParameter("id", id);
        try {
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<SmsMessage> getAllSmsMessages(String service, String provider, String sender, String recipient, String status, String celcomMessageId, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SmsMessage> cq = cb.createQuery(SmsMessage.class);
        Root<SmsMessage> smsMessage = cq.from(SmsMessage.class);

        List<Predicate> predicates = new ArrayList<>();

        if (service != null && !service.isEmpty()) {
            predicates.add(cb.equal(smsMessage.get("service"), service));
        }
        if (provider != null && !provider.isEmpty()) {
            predicates.add(cb.equal(smsMessage.get("provider"), provider));
        }
        if (sender != null && !sender.isEmpty()) {
            predicates.add(cb.like(cb.lower(smsMessage.get("sender")), "%" + sender.toLowerCase() + "%"));
        }
        if (recipient != null && !recipient.isEmpty()) {
            predicates.add(cb.like(cb.lower(smsMessage.get("recipient")), "%" + recipient.toLowerCase() + "%"));
        }
        if (status != null && !status.isEmpty()) {
            predicates.add(cb.equal(smsMessage.get("status"), status));
        }
        if (celcomMessageId != null && !celcomMessageId.isEmpty()) {
            predicates.add(cb.equal(smsMessage.get("celcomMessageId"), celcomMessageId));
        }
        if (createdAtStartDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(smsMessage.get("createdAt"), createdAtStartDate));
        }
        if (createdAtEndDate != null) {
            predicates.add(cb.lessThanOrEqualTo(smsMessage.get("createdAt"), createdAtEndDate));
        }
        if (updatedAtStartDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(smsMessage.get("updatedAt"), updatedAtStartDate));
        }
        if (updatedAtEndDate != null) {
            predicates.add(cb.lessThanOrEqualTo(smsMessage.get("updatedAt"), updatedAtEndDate));
        }

        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(smsMessage.get("createdAt")));

        TypedQuery<SmsMessage> query = entityManager.createQuery(cq);

        // Convert 1-based pagination to 0-based for internal use
        int pageNumber = pageable.getPageNumber() > 0 ? pageable.getPageNumber() - 1 : 0;
        int pageSize = pageable.getPageSize();

        // Get total count
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<SmsMessage> countRoot = countQuery.from(SmsMessage.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(predicates.toArray(new Predicate[0]));
        Long totalCount = entityManager.createQuery(countQuery).getSingleResult();

        // Apply pagination
        query.setFirstResult(pageNumber * pageSize);
        query.setMaxResults(pageSize);

        List<SmsMessage> results = query.getResultList();

        // Create PageRequest with 1-based page number for the returned Page
        Pageable adjustedPageable = PageRequest.of(pageNumber, pageSize, pageable.getSort());

        return new PageImpl<>(results, adjustedPageable, totalCount);
    }
}
