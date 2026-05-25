package com.example.admin.role.dto;

import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.role.entity.AdminRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class RoleDtos {

    private RoleDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleRequest {
        @NotBlank
        private String roleCode;

        @NotBlank
        private String roleName;

        @NotNull
        private Boolean enabled;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleSummary {
        private Long id;
        private String roleCode;
        private String roleName;

        public static RoleSummary from(AdminRole role) {
            return RoleSummary.builder()
                    .id(role.getId())
                    .roleCode(role.getRoleCode())
                    .roleName(role.getRoleName())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleResponse {
        private Long id;
        private String roleCode;
        private String roleName;
        private boolean enabled;

        public static RoleResponse from(AdminRole role) {
            return RoleResponse.builder()
                    .id(role.getId())
                    .roleCode(role.getRoleCode())
                    .roleName(role.getRoleName())
                    .enabled(role.isEnabled())
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuAssignmentRequest {
        @NotEmpty
        private Set<Long> menuIds;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleMenusResponse {
        private Long roleId;
        private Set<MenuDtos.MenuResponse> menus;
    }
}

