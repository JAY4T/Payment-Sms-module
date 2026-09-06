package com.module.paymentsms.dao;

import com.module.paymentsms.entity.Bank;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BankDaoImpl implements BankDao {

    private final EntityManager entityManager;

    @Autowired
    public BankDaoImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Bank createBank(Bank bank) {
        entityManager.persist(bank);
        return bank;
    }

    @Override
    public Bank updateBank(Bank bank) {
        entityManager.merge(bank);
        return bank;
    }

    @Override
    public Bank getBankByIntasendBankCode(String intasendBankCode) {
        try {
            TypedQuery<Bank> query = entityManager.createQuery(
                    "SELECT b FROM Bank b WHERE b.intasendBankCode = :intasendBankCode", Bank.class);
            query.setParameter("intasendBankCode", intasendBankCode);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Bank> getAllBanks() {
        TypedQuery<Bank> query = entityManager.createQuery(
                "SELECT b FROM Bank b ORDER BY b.bankName", Bank.class);
        return query.getResultList();
    }
}
