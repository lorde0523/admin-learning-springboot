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
        String pattern = ".*" + likeKeywordToRegex(keyword.toUpperCase(Locale.ROOT)) + ".*";

        query.select(user);
        query.where(builder.equal(
                builder.function(
                        "regexp_like",
                        Boolean.class,
                        builder.upper(user.get("loginId")),
                        builder.literal(pattern)),
                true));

        return entityManager.createQuery(query).getResultList();
    }

    private String likeKeywordToRegex(String keyword) {
        StringBuilder regex = new StringBuilder();
        for (int index = 0; index < keyword.length(); index++) {
            char character = keyword.charAt(index);
            if (character == '%') {
                regex.append(".*");
            } else if (character == '_') {
                regex.append('.');
            } else {
                regex.append("\\Q").append(character).append("\\E");
            }
        }
        return regex.toString();
    }
}
