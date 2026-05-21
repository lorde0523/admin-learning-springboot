package com.example.admin.menu.dto;

import com.example.admin.menu.entity.AdminMenu;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.util.List;

public final class MenuDtos {

    private MenuDtos() {
    }

    public record MenuRequest(
            @NotBlank String menuCode,
            @NotBlank String menuName,
            Long parentMenuId,
            @PositiveOrZero int sortOrder,
            @NotNull Boolean enabled) {
    }

    public record MenuResponse(
            Long id,
            String menuCode,
            String menuName,
            Long parentMenuId,
            int sortOrder,
            boolean enabled,
            String createdBy,
            LocalDateTime createdAt) {

        public static MenuResponse from(AdminMenu menu) {
            return new MenuResponse(
                    menu.getId(),
                    menu.getMenuCode(),
                    menu.getMenuName(),
                    menu.getParentMenuId(),
                    menu.getSortOrder(),
                    menu.isEnabled(),
                    menu.getCreatedBy(),
                    menu.getCreatedAt());
        }
    }

    public record MenuBulkRequest(@NotEmpty List<@Valid MenuRequest> menus) {
    }

    public record BulkInsertResponse(int insertedCount) {
    }
}

