package com.example.admin.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.admin.common.config.JpaAuditConfig;
import com.example.admin.role.entity.AdminRole;
import com.example.admin.role.repository.AdminRoleRepository;
import com.example.admin.user.entity.AdminUser;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@DataJpaTest
@Import(JpaAuditConfig.class)
class AdminUserRepositoryTests {

    @Autowired
    private AdminUserRepository userRepository;

    @Autowired
    private AdminRoleRepository roleRepository;

    @Test
    void searchesLoginIdWithUpperFunction() {
        userRepository.save(AdminUser.create("mint.admin", "Mint Admin", true));

        var users = userRepository.searchByLoginIdIgnoreCase("MINT");

        assertThat(users).extracting(AdminUser::getLoginId).containsExactly("mint.admin");
    }

    @Test
    void loadsAssignedRolesAndAuditColumns() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "repository-test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var role = roleRepository.save(AdminRole.create("ADMIN", "Administrator", true));
        var user = AdminUser.create("role.user", "Role User", true);
        user.assignRoles(Set.of(role));

        var saved = userRepository.saveAndFlush(user);
        var loaded = userRepository.findWithRolesById(saved.getId()).orElseThrow();

        assertThat(loaded.getRoles()).extracting(AdminRole::getRoleCode).containsExactly("ADMIN");
        assertThat(loaded.getCreatedBy()).isEqualTo("repository-test");
        assertThat(loaded.getCreatedAt()).isNotNull();
        RequestContextHolder.resetRequestAttributes();
    }
}
