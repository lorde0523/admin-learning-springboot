package com.example.admin.user.repository;

import com.example.admin.user.entity.AdminUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long>, AdminUserSearchRepository {

    @EntityGraph(attributePaths = "roles")
    Optional<AdminUser> findWithRolesById(Long id);
}
