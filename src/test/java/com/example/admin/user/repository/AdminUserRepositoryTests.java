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
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;
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

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void searchesLoginIdWithUpperFunction() {
        userRepository.save(AdminUser.create("mint.admin", "Mint Admin", true));
        entityManager.flush();
        entityManager.clear();

        var users = userRepository.searchByLoginIdIgnoreCase("MINT");

        assertThat(users).extracting(AdminUser::getLoginId).containsExactly("mint.admin");
    }

    @Test
    void treatsKeywordWildcardAsLikePattern() {
        userRepository.save(AdminUser.create("mint.admin", "Mint Admin", true));
        entityManager.flush();
        entityManager.clear();

        var users = userRepository.searchByLoginIdIgnoreCase("%");

        assertThat(users).extracting(AdminUser::getLoginId).contains("mint.admin");
    }

    @Test
    void treatsEmbeddedKeywordWildcardsAsLikePattern() {
        userRepository.save(AdminUser.create("mint.admin", "Mint Admin", true));
        userRepository.save(AdminUser.create("guest.user", "Guest User", true));
        entityManager.flush();
        entityManager.clear();

        var percentUsers = userRepository.searchByLoginIdIgnoreCase("M%");
        var underscoreUsers = userRepository.searchByLoginIdIgnoreCase("m_nt");

        assertThat(percentUsers).extracting(AdminUser::getLoginId).contains("mint.admin");
        assertThat(underscoreUsers).extracting(AdminUser::getLoginId).contains("mint.admin");
    }

    @Test
    void searchesLoginIdAfterRoleAssignment() {
        var role = roleRepository.save(AdminRole.create("ADMIN", "Administrator", true));
        var user = AdminUser.create("mint.admin", "Mint Admin", true);
        user.assignRoles(Set.of(role));
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        var users = userRepository.searchByLoginIdIgnoreCase("MINT");

        assertThat(users).extracting(AdminUser::getLoginId).containsExactly("mint.admin");
    }

    @Test
    void findsEnabledUserWithLoginIdSpecification() {
        userRepository.save(AdminUser.create("mint.admin", "Mint Admin", true));
        userRepository.save(AdminUser.create("mint.disabled", "Mint Disabled", false));
        entityManager.flush();
        entityManager.clear();

        var users = userRepository.findAll(Specification.allOf(
                AdminUserSpecifications.loginIdContains("MINT"),
                AdminUserSpecifications.enabledEquals(true)));

        assertThat(users).extracting(AdminUser::getLoginId).containsExactly("mint.admin");
    }

    @Test
    void excludesEnabledUserWhenSpecificationMatchesDisabledUsers() {
        userRepository.save(AdminUser.create("mint.admin", "Mint Admin", true));
        entityManager.flush();
        entityManager.clear();

        var users = userRepository.findAll(Specification.allOf(
                AdminUserSpecifications.loginIdContains("MINT"),
                AdminUserSpecifications.enabledEquals(false)));

        assertThat(users).extracting(AdminUser::getLoginId).doesNotContain("mint.admin");
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
