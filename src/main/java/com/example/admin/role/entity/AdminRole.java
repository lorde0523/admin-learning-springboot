package com.example.admin.role.entity;

import com.example.admin.common.audit.AuditEntity;
import com.example.admin.menu.entity.AdminMenu;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "ADMIN_ROLE")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminRole extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "admin_role_seq")
    @SequenceGenerator(name = "admin_role_seq", sequenceName = "ADMIN_ROLE_SEQ", allocationSize = 1)
    @Column(name = "ROLE_ID")
    private Long id;

    @Column(name = "ROLE_CODE", nullable = false, unique = true, length = 50)
    private String roleCode;

    @Column(name = "ROLE_NAME", nullable = false, length = 100)
    private String roleName;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled;

    @ManyToMany
    @JoinTable(
            name = "ADMIN_ROLE_MENU",
            joinColumns = @JoinColumn(name = "ROLE_ID"),
            inverseJoinColumns = @JoinColumn(name = "MENU_ID"))
    private Set<AdminMenu> menus = new LinkedHashSet<>();

    private AdminRole(String roleCode, String roleName, boolean enabled) {
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.enabled = enabled;
    }

    public static AdminRole create(String roleCode, String roleName, boolean enabled) {
        return new AdminRole(roleCode, roleName, enabled);
    }

    public void update(String roleName, boolean enabled) {
        this.roleName = roleName;
        this.enabled = enabled;
    }

    public void assignMenus(Set<AdminMenu> menus) {
        this.menus.clear();
        this.menus.addAll(menus);
    }
}
