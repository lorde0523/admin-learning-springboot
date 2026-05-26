package com.example.admin.menu.dto.adminmenu;

import com.example.admin.menu.entity.AdminMenu;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuResponse {

    private Long id;
    private String menuCode;
    private String menuName;
    private Long parentMenuId;
    private int sortOrder;
    private boolean enabled;
    private String createdBy;
    private LocalDateTime createdAt;

    public static MenuResponse from(AdminMenu menu) {
        return MenuResponse.builder()
                .id(menu.getId())
                .menuCode(menu.getMenuCode())
                .menuName(menu.getMenuName())
                .parentMenuId(menu.getParentMenuId())
                .sortOrder(menu.getSortOrder())
                .enabled(menu.isEnabled())
                .createdBy(menu.getCreatedBy())
                .createdAt(menu.getCreatedAt())
                .build();
    }
}
