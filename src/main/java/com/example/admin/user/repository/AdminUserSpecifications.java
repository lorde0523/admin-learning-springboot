package com.example.admin.user.repository;

import com.example.admin.user.entity.AdminUser;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class AdminUserSpecifications {

    private AdminUserSpecifications() {
    }

    public static Specification<AdminUser> loginIdContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        return (root, query, criteriaBuilder) -> criteriaBuilder.like(
                criteriaBuilder.lower(root.get("loginId")),
                "%" + keyword.toLowerCase(Locale.ROOT) + "%");
    }

    public static Specification<AdminUser> enabledEquals(Boolean enabled) {
        if (enabled == null) {
            return null;
        }

        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("enabled"), enabled);
    }
}
