package com.example.admin.user.mapper;

import com.example.admin.role.dto.RoleDtos;
import com.example.admin.user.dto.UserDtos;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminUserMapper {

    UserRow findById(Long id);

    List<UserRow> search(@Param("loginKeyword") String loginKeyword);

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    class UserRow {
        private Long id;
        private String loginId;
        private String name;
        private boolean enabled;
        private String createdBy;
        private LocalDateTime createdAt;
        private String updatedBy;
        private LocalDateTime updatedAt;

        public UserDtos.UserResponse toResponse(List<RoleDtos.RoleSummary> roles) {
            return new UserDtos.UserResponse(
                    id, loginId, name, enabled, createdBy, createdAt, updatedBy, updatedAt, roles);
        }
    }
}

