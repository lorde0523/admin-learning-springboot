package com.example.admin.user.repository;

import com.example.admin.user.entity.AdminUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Locale;

public class AdminUserSearchRepositoryImpl implements AdminUserSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<AdminUser> searchByLoginIdIgnoreCase(String keyword) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AdminUser> query = builder.createQuery(AdminUser.class);
        Root<AdminUser> user = query.from(AdminUser.class);
        String normalizedKeyword = keyword.toUpperCase(Locale.ROOT);

        query.select(user);
        if (!"%".equals(keyword)) {
            query.where(builder.greaterThan(
                    builder.locate(builder.upper(user.get("loginId")), normalizedKeyword),
                    0));
        }

        return entityManager.createQuery(query).getResultList();
    }
}
