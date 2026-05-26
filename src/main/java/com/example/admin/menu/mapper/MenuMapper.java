package com.example.admin.menu.mapper;

import com.example.admin.menu.dto.adminmenu.MenuGridRow;
import com.example.admin.menu.dto.adminmenu.MenuRequest;
import com.example.admin.menu.dto.adminmenu.MenuResponse;
import com.example.admin.menu.entity.AdminMenu;
import org.springframework.stereotype.Component;

@Component
public class MenuMapper {

    public AdminMenu toEntity(MenuRequest request) {
        return AdminMenu.create(
                request.getMenuCode(),
                request.getMenuName(),
                request.getParentMenuId(),
                request.getSortOrder(),
                request.getEnabled());
    }

    public void updateEntity(AdminMenu menu, MenuGridRow row) {
        updateEntity(menu, (MenuRequest) row);
    }

    public void updateEntity(AdminMenu menu, MenuRequest request) {
        menu.update(request.getMenuName(), request.getParentMenuId(), request.getSortOrder(), request.getEnabled());
    }

    public MenuResponse toResponse(AdminMenu menu) {
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
