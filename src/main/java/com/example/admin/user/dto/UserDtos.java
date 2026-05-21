package com.example.admin.user.dto;

import com.example.admin.role.dto.RoleDtos;
import com.example.admin.user.entity.AdminUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserRequest(
            @NotBlank String loginId,
            @NotBlank String name,
            @NotNull Boolean enabled) {
    }

    public record RoleAssignmentRequest(@NotEmpty Set<Long> roleIds) {
    }

    public record UserResponse(
            Long id,
            String loginId,
            String name,
            boolean enabled,
            String createdBy,
            LocalDateTime createdAt,
            String updatedBy,
            LocalDateTime updatedAt,
            List<RoleDtos.RoleSummary> roles) {

        public static UserResponse from(AdminUser user) {
            return new UserResponse(
                    user.getId(),
                    user.getLoginId(),
                    user.getName(),
                    user.isEnabled(),
                    user.getCreatedBy(),
                    user.getCreatedAt(),
                    user.getUpdatedBy(),
                    user.getUpdatedAt(),
                    user.getRoles().stream().map(RoleDtos.RoleSummary::from).toList());
        }
    }
}
