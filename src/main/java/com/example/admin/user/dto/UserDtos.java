package com.example.admin.user.dto;

import com.example.admin.role.dto.RoleDtos;
import com.example.admin.user.entity.AdminUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class UserDtos {

    private UserDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRequest {
        @NotBlank
        private String loginId;

        @NotBlank
        private String name;

        @NotNull
        private Boolean enabled;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleAssignmentRequest {
        @NotEmpty
        private Set<Long> roleIds;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserResponse {
        private Long id;
        private String loginId;
        private String name;
        private boolean enabled;
        private String createdBy;
        private LocalDateTime createdAt;
        private String updatedBy;
        private LocalDateTime updatedAt;
        private List<RoleDtos.RoleSummary> roles;

        public static UserResponse from(AdminUser user) {
            return UserResponse.builder()
                    .id(user.getId())
                    .loginId(user.getLoginId())
                    .name(user.getName())
                    .enabled(user.isEnabled())
                    .createdBy(user.getCreatedBy())
                    .createdAt(user.getCreatedAt())
                    .updatedBy(user.getUpdatedBy())
                    .updatedAt(user.getUpdatedAt())
                    .roles(user.getRoles().stream().map(RoleDtos.RoleSummary::from).toList())
                    .build();
        }
    }
}
