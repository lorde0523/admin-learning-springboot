package com.example.admin.user.mapper;

import com.example.admin.role.dto.RoleDtos;
import com.example.admin.user.dto.UserDtos;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminUserMapper {

    Long nextId();

    int insert(@Param("id") Long id, @Param("request") UserDtos.UserRequest request, @Param("auditor") String auditor);

    UserRow findById(Long id);

    List<UserRow> search(@Param("loginKeyword") String loginKeyword);

    int update(@Param("id") Long id, @Param("request") UserDtos.UserRequest request, @Param("auditor") String auditor);

    int delete(Long id);

    int deleteRoles(Long userId);

    int insertRoles(@Param("userId") Long userId, @Param("roleIds") Set<Long> roleIds);

    record UserRow(
            Long id,
            String loginId,
            String name,
            boolean enabled,
            String createdBy,
            LocalDateTime createdAt,
            String updatedBy,
            LocalDateTime updatedAt) {

        public UserDtos.UserResponse toResponse(List<RoleDtos.RoleSummary> roles) {
            return new UserDtos.UserResponse(
                    id, loginId, name, enabled, createdBy, createdAt, updatedBy, updatedAt, roles);
        }
    }
}

