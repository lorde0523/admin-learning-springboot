package com.example.admin.user.repository;

import com.example.admin.user.entity.AdminUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    @Query("""
            select user
              from AdminUser user
             where upper(user.loginId) like upper(concat('%', :keyword, '%'))
            """)
    List<AdminUser> searchByLoginIdIgnoreCase(@Param("keyword") String keyword);

    @EntityGraph(attributePaths = "roles")
    Optional<AdminUser> findWithRolesById(Long id);
}
