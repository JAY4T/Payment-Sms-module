package com.module.paymentsms.dao;

import com.module.paymentsms.entity.Wallet;
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
public class WalletDaoImpl implements WalletDao{

    private final EntityManager entityManager;

    @Autowired
    public WalletDaoImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Wallet createWallet(Wallet wallet) {
        entityManager.persist(wallet);
        return wallet;
    }

    @Override
    public Wallet updateWallet(Wallet wallet) {
        entityManager.merge(wallet);
        return wallet;
    }

    @Override
    public Wallet getWalletById(Long id) {
        try {
            TypedQuery<Wallet> query = entityManager.createQuery(
                    "SELECT w FROM Wallet w WHERE w.id = :id", Wallet.class);
            query.setParameter("id", id);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Wallet getWalletByIntasendWalletId(String intasendWalletId) {
        try {
            TypedQuery<Wallet> query = entityManager.createQuery(
                    "SELECT w FROM Wallet w WHERE w.intasendWalletId = :intasendWalletId", Wallet.class);
            query.setParameter("intasendWalletId", intasendWalletId);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<Wallet> getAllWallets(String name, Boolean isSystemWallet, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Wallet> cq = cb.createQuery(Wallet.class);
        Root<Wallet> wallet = cq.from(Wallet.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (name != null && !name.isEmpty()) {
            predicates.add(cb.like(cb.lower(wallet.get("name")), "%" + name.toLowerCase() + "%"));
        }
        if (isSystemWallet != null) {
            predicates.add(cb.equal(wallet.get("isSystemWallet"), isSystemWallet));
        }
        if (createdAtStartDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(wallet.get("createdAt"), createdAtStartDate));
        }
        if (createdAtEndDate != null) {
            predicates.add(cb.lessThanOrEqualTo(wallet.get("createdAt"), createdAtEndDate));
        }
        if (updatedAtStartDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(wallet.get("updatedAt"), updatedAtStartDate));
        }
        if (updatedAtEndDate != null) {
            predicates.add(cb.lessThanOrEqualTo(wallet.get("updatedAt"), updatedAtEndDate));
        }
        
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(wallet.get("createdAt")));
        
        TypedQuery<Wallet> query = entityManager.createQuery(cq);
        
        // Convert 1-based pagination to 0-based for internal use
        int pageNumber = pageable.getPageNumber() > 0 ? pageable.getPageNumber() - 1 : 0;
        int pageSize = pageable.getPageSize();
        
        // Get total count
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Wallet> countRoot = countQuery.from(Wallet.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(predicates.toArray(new Predicate[0]));
        Long totalCount = entityManager.createQuery(countQuery).getSingleResult();
        
        // Apply pagination
        query.setFirstResult(pageNumber * pageSize);
        query.setMaxResults(pageSize);
        
        List<Wallet> results = query.getResultList();
        
        // Create PageRequest with 1-based page number for the returned Page
        Pageable adjustedPageable = PageRequest.of(pageNumber, pageSize, pageable.getSort());
        
        return new PageImpl<>(results, adjustedPageable, totalCount);
    }
}
