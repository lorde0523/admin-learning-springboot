package com.example.admin.menu.entity;

import com.example.admin.common.audit.AuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ADMIN_MENU")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminMenu extends AuditEntity {

    @Id
    @Column(name = "MENU_ID")
    private Long id;

    @Column(name = "MENU_CODE", nullable = false, unique = true, length = 50)
    private String menuCode;

    @Column(name = "MENU_NAME", nullable = false, length = 100)
    private String menuName;

    @Column(name = "PARENT_MENU_ID")
    private Long parentMenuId;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled;

    private AdminMenu(Long id, String menuCode, String menuName, Long parentMenuId, int sortOrder, boolean enabled) {
        this.id = id;
        this.menuCode = menuCode;
        this.menuName = menuName;
        this.parentMenuId = parentMenuId;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }

    public static AdminMenu create(
            Long id, String menuCode, String menuName, Long parentMenuId, int sortOrder, boolean enabled) {
        return new AdminMenu(id, menuCode, menuName, parentMenuId, sortOrder, enabled);
    }

    public void update(String menuName, Long parentMenuId, int sortOrder, boolean enabled) {
        this.menuName = menuName;
        this.parentMenuId = parentMenuId;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }
}
