package com.example.admin.role.repository;

import com.example.admin.role.entity.AdminRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRoleRepository extends JpaRepository<AdminRole, Long> {

    Optional<AdminRole> findByRoleCode(String roleCode);

    @EntityGraph(attributePaths = "menus")
    Optional<AdminRole> findWithMenusById(Long id);
}
