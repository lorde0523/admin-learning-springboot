package com.example.admin.user.repository;

import com.example.admin.user.entity.AdminUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    default List<AdminUser> searchByLoginIdIgnoreCase(String keyword) {
        return findByLoginIdContainingIgnoreCase(keyword);
    }

    List<AdminUser> findByLoginIdContainingIgnoreCase(String keyword);

    @EntityGraph(attributePaths = "roles")
    Optional<AdminUser> findWithRolesById(Long id);
}
