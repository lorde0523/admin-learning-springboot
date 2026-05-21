package com.example.admin.role.mapper;

import com.example.admin.role.dto.RoleDtos;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminRoleMapper {

    Long nextId();

    int insert(@Param("id") Long id, @Param("request") RoleDtos.RoleRequest request, @Param("auditor") String auditor);

    RoleRow findById(Long id);

    List<RoleRow> findAll();

    List<RoleRow> findByUserId(Long userId);

    int update(@Param("id") Long id, @Param("request") RoleDtos.RoleRequest request, @Param("auditor") String auditor);

    int delete(Long id);

    int deleteMenus(Long roleId);

    int insertMenus(@Param("roleId") Long roleId, @Param("menuIds") Set<Long> menuIds);

    record RoleRow(Long id, String roleCode, String roleName, boolean enabled) {

        public RoleDtos.RoleResponse toResponse() {
            return new RoleDtos.RoleResponse(id, roleCode, roleName, enabled);
        }

        public RoleDtos.RoleSummary toSummary() {
            return new RoleDtos.RoleSummary(id, roleCode, roleName);
        }
    }
}

