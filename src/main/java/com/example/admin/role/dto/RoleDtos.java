package com.example.admin.role.dto;

import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.role.entity.AdminRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleRequest(
            @NotBlank String roleCode,
            @NotBlank String roleName,
            @NotNull Boolean enabled) {
    }

    public record RoleSummary(Long id, String roleCode, String roleName) {

        public static RoleSummary from(AdminRole role) {
            return new RoleSummary(role.getId(), role.getRoleCode(), role.getRoleName());
        }
    }

    public record RoleResponse(Long id, String roleCode, String roleName, boolean enabled) {

        public static RoleResponse from(AdminRole role) {
            return new RoleResponse(role.getId(), role.getRoleCode(), role.getRoleName(), role.isEnabled());
        }
    }

    public record MenuAssignmentRequest(@NotEmpty Set<Long> menuIds) {
    }

    public record RoleMenusResponse(Long roleId, Set<MenuDtos.MenuResponse> menus) {
    }
}

