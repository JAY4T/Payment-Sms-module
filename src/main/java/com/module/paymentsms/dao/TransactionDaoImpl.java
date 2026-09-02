package com.module.paymentsms.dao;

import com.module.paymentsms.entity.Transaction;
import com.module.paymentsms.entity.TransactionCallback;
import com.module.paymentsms.entity.TransactionMetaData;
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
public class TransactionDaoImpl implements TransactionDao{

    private final EntityManager entityManager;

    @Autowired
    public TransactionDaoImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Transaction createTransaction(Transaction transaction) {
        entityManager.persist(transaction);
        return transaction;
    }

    @Override
    public Transaction updateTransaction(Transaction transaction) {
        entityManager.merge(transaction);
        return transaction;
    }

    @Override
    public Transaction getTransactionById(Long id) {
        try {
            TypedQuery<Transaction> query = entityManager.createQuery(
                    "SELECT t FROM Transaction t WHERE t.id = :id", Transaction.class);
            query.setParameter("id", id);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Transaction getTransactionByReference(String reference) {
        try {
            TypedQuery<Transaction> query = entityManager.createQuery(
                    "SELECT t FROM Transaction t WHERE t.transactionRef = :reference", Transaction.class);
            query.setParameter("reference", reference);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public Transaction getTransactionByIntasendTrackingId(String trackingId) {
        try {
            TypedQuery<Transaction> query = entityManager.createQuery(
                    "SELECT t FROM Transaction t WHERE t.intasendTrackingId = :trackingId", Transaction.class);
            query.setParameter("trackingId", trackingId);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<Transaction> getAllTransactions(Long walletId, String provider, String sender, String method, String type, String status, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Transaction> cq = cb.createQuery(Transaction.class);
        Root<Transaction> transaction = cq.from(Transaction.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (walletId != null) {
            predicates.add(cb.equal(transaction.get("wallet").get("id"), walletId));
        }
        if (provider != null && !provider.isEmpty()) {
            predicates.add(cb.equal(transaction.get("provider"), provider));
        }
        if (sender != null && !sender.isEmpty()) {
            predicates.add(cb.equal(transaction.get("sender"), sender));
        }
        if (method != null && !method.isEmpty()) {
            predicates.add(cb.equal(transaction.get("method"), method));
        }
        if (type != null && !type.isEmpty()) {
            predicates.add(cb.equal(transaction.get("type"), type));
        }
        if (status != null && !status.isEmpty()) {
            predicates.add(cb.equal(transaction.get("status"), status));
        }
        if (createdAtStartDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(transaction.get("createdAt"), createdAtStartDate));
        }
        if (createdAtEndDate != null) {
            predicates.add(cb.lessThanOrEqualTo(transaction.get("createdAt"), createdAtEndDate));
        }
        if (updatedAtStartDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(transaction.get("updatedAt"), updatedAtStartDate));
        }
        if (updatedAtEndDate != null) {
            predicates.add(cb.lessThanOrEqualTo(transaction.get("updatedAt"), updatedAtEndDate));
        }
        
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(transaction.get("createdAt")));
        
        TypedQuery<Transaction> query = entityManager.createQuery(cq);
        
        // Convert 1-based pagination to 0-based for internal use
        int pageNumber = pageable.getPageNumber() > 0 ? pageable.getPageNumber() - 1 : 0;
        int pageSize = pageable.getPageSize();
        
        // Get total count
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Transaction> countRoot = countQuery.from(Transaction.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(predicates.toArray(new Predicate[0]));
        Long totalCount = entityManager.createQuery(countQuery).getSingleResult();
        
        // Apply pagination
        query.setFirstResult(pageNumber * pageSize);
        query.setMaxResults(pageSize);
        
        List<Transaction> results = query.getResultList();
        
        // Create PageRequest with 1-based page number for the returned Page
        Pageable adjustedPageable = PageRequest.of(pageNumber, pageSize, pageable.getSort());
        
        return new PageImpl<>(results, adjustedPageable, totalCount);
    }

    @Override
    public TransactionCallback createTransactionCallback(TransactionCallback transactionCallback) {
        entityManager.persist(transactionCallback);
        return transactionCallback;
    }

    @Override
    public Page<TransactionCallback> getAllTransactionCallbacks(Long transactionId, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TransactionCallback> cq = cb.createQuery(TransactionCallback.class);
        Root<TransactionCallback> callback = cq.from(TransactionCallback.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (transactionId != null) {
            predicates.add(cb.equal(callback.get("transaction").get("id"), transactionId));
        }
        if (createdAtStartDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(callback.get("createdAt"), createdAtStartDate));
        }
        if (createdAtEndDate != null) {
            predicates.add(cb.lessThanOrEqualTo(callback.get("createdAt"), createdAtEndDate));
        }
        
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(callback.get("createdAt")));
        
        TypedQuery<TransactionCallback> query = entityManager.createQuery(cq);
        
        // Convert 1-based pagination to 0-based for internal use
        int pageNumber = pageable.getPageNumber() > 0 ? pageable.getPageNumber() - 1 : 0;
        int pageSize = pageable.getPageSize();
        
        // Get total count
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<TransactionCallback> countRoot = countQuery.from(TransactionCallback.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(predicates.toArray(new Predicate[0]));
        Long totalCount = entityManager.createQuery(countQuery).getSingleResult();
        
        // Apply pagination
        query.setFirstResult(pageNumber * pageSize);
        query.setMaxResults(pageSize);
        
        List<TransactionCallback> results = query.getResultList();
        
        // Create PageRequest with 1-based page number for the returned Page
        Pageable adjustedPageable = PageRequest.of(pageNumber, pageSize, pageable.getSort());
        
        return new PageImpl<>(results, adjustedPageable, totalCount);
    }
    
    @Override
    public TransactionMetaData createTransactionMetaData(TransactionMetaData transactionMetaData) {
        entityManager.persist(transactionMetaData);
        return transactionMetaData;
    }

    @Override
    public List<Transaction> getPendingSendMoneyBatches() {
        TypedQuery<Transaction> query = entityManager.createQuery(
                "SELECT t FROM Transaction t WHERE t.hasBatch = true AND t.status IN ('PENDING', 'PROCESSING') AND t.intasendTrackingId IS NOT NULL",
                Transaction.class);
        return query.getResultList();
    }

    @Override
    public List<Transaction> getPendingCollectionTransactions() {
        TypedQuery<Transaction> query = entityManager.createQuery(
                "SELECT t FROM Transaction t WHERE t.status IN ('PENDING', 'PROCESSING') AND t.invoiceId IS NOT NULL",
                Transaction.class);
        return query.getResultList();
    }

    @Override
    public List<Transaction> getCollectionTransactionsPendingClearingStatus() {
        TypedQuery<Transaction> query = entityManager.createQuery(
                "SELECT t FROM Transaction t WHERE t.status = 'COMPLETED' AND t.invoiceId IS NOT NULL " +
                        "AND (t.clearingStatus IS NULL OR t.clearingStatus <> 'AVAILABLE')",
                Transaction.class);
        return query.getResultList();
    }
}
