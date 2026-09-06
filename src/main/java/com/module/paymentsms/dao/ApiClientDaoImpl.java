package com.module.paymentsms.dao;

import com.module.paymentsms.entity.ApiClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ApiClientDaoImpl implements ApiClientDao {

    private final EntityManager entityManager;

    @Autowired
    public ApiClientDaoImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public ApiClient createApiClient(ApiClient apiClient) {
        entityManager.persist(apiClient);
        return apiClient;
    }

    @Override
    public ApiClient updateApiClient(ApiClient apiClient) {
        entityManager.merge(apiClient);
        return apiClient;
    }

    @Override
    public ApiClient getApiClientById(Long id) {
        return entityManager.find(ApiClient.class, id);
    }

    @Override
    public ApiClient getApiClientByApiKey(String apiKey) {
        try {
            TypedQuery<ApiClient> query = entityManager.createQuery(
                    "SELECT a FROM ApiClient a WHERE a.apiKey = :apiKey", ApiClient.class);
            query.setParameter("apiKey", apiKey);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ApiClient> getAllApiClients() {
        TypedQuery<ApiClient> query = entityManager.createQuery(
                "SELECT a FROM ApiClient a ORDER BY a.createdAt DESC", ApiClient.class);
        return query.getResultList();
    }
}
