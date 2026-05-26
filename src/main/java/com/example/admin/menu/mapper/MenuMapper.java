package com.example.admin.menu.mapper;

import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.menu.entity.AdminMenu;
import org.springframework.stereotype.Component;

@Component
public class MenuMapper {

    public AdminMenu toEntity(MenuDtos.MenuRequest request) {
        return AdminMenu.create(
                request.getMenuCode(),
                request.getMenuName(),
                request.getParentMenuId(),
                request.getSortOrder(),
                request.getEnabled());
    }

    public void updateEntity(AdminMenu menu, MenuDtos.MenuGridRow row) {
        updateEntity(menu, (MenuDtos.MenuRequest) row);
    }

    public void updateEntity(AdminMenu menu, MenuDtos.MenuRequest request) {
        menu.update(request.getMenuName(), request.getParentMenuId(), request.getSortOrder(), request.getEnabled());
    }

    public MenuDtos.MenuResponse toResponse(AdminMenu menu) {
        return MenuDtos.MenuResponse.builder()
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
