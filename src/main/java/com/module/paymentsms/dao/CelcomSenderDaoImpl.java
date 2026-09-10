package com.module.paymentsms.dao;

import com.module.paymentsms.entity.CelcomSender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CelcomSenderDaoImpl implements CelcomSenderDao {

    private final EntityManager entityManager;

    @Autowired
    public CelcomSenderDaoImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public CelcomSender createCelcomSender(CelcomSender celcomSender) {
        entityManager.persist(celcomSender);
        return celcomSender;
    }

    @Override
    public CelcomSender updateCelcomSender(CelcomSender celcomSender) {
        entityManager.merge(celcomSender);
        return celcomSender;
    }

    @Override
    public CelcomSender getCelcomSenderById(Long id) {
        return entityManager.find(CelcomSender.class, id);
    }

    @Override
    public CelcomSender getCelcomSenderByShortcode(String shortcode) {
        try {
            TypedQuery<CelcomSender> query = entityManager.createQuery(
                    "SELECT c FROM CelcomSender c WHERE c.shortcode = :shortcode", CelcomSender.class);
            query.setParameter("shortcode", shortcode);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<CelcomSender> getAllCelcomSenders() {
        TypedQuery<CelcomSender> query = entityManager.createQuery(
                "SELECT c FROM CelcomSender c ORDER BY c.createdAt DESC", CelcomSender.class);
        return query.getResultList();
    }
}
