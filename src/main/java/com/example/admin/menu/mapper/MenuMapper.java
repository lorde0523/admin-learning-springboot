package com.example.admin.menu.mapper;

import com.example.admin.menu.dto.adminmenu.MenuGridRow;
import com.example.admin.menu.dto.adminmenu.MenuRequest;
import com.example.admin.menu.dto.adminmenu.MenuResponse;
import com.example.admin.menu.entity.AdminMenu;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface MenuMapper {

    default AdminMenu toEntity(MenuGridRow request) {
        return AdminMenu.create(
                request.getId(),
                request.getMenuCode(),
                request.getMenuName(),
                request.getParentMenuId(),
                request.getSortOrder(),
                request.getEnabled());
    }

    default void updateEntity(@MappingTarget AdminMenu menu, MenuGridRow row) {
        updateEntity(menu, (MenuRequest) row);
    }

    default void updateEntity(@MappingTarget AdminMenu menu, MenuRequest request) {
        menu.update(request.getMenuName(), request.getParentMenuId(), request.getSortOrder(), request.getEnabled());
    }

    default MenuResponse toResponse(AdminMenu menu) {
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
