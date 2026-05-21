package com.example.admin.user.entity;

import com.example.admin.common.audit.AuditEntity;
import com.example.admin.role.entity.AdminRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ADMIN_USER")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminUser extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "admin_user_seq")
    @SequenceGenerator(name = "admin_user_seq", sequenceName = "ADMIN_USER_SEQ", allocationSize = 1)
    @Column(name = "USER_ID")
    private Long id;

    @Column(name = "LOGIN_ID", nullable = false, unique = true, length = 100)
    private String loginId;

    @Column(name = "USER_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ADMIN_USER_ROLE",
            joinColumns = @JoinColumn(name = "USER_ID"),
            inverseJoinColumns = @JoinColumn(name = "ROLE_ID"))
    private Set<AdminRole> roles = new LinkedHashSet<>();

    private AdminUser(String loginId, String name, boolean enabled) {
        this.loginId = loginId;
        this.name = name;
        this.enabled = enabled;
    }

    public static AdminUser create(String loginId, String name, boolean enabled) {
        return new AdminUser(loginId, name, enabled);
    }

    public void update(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    public void assignRoles(Set<AdminRole> roles) {
        this.roles.clear();
        this.roles.addAll(roles);
    }
}

