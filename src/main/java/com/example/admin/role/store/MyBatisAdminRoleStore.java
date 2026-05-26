package com.example.admin.role.store;

import com.example.admin.role.dto.RoleDtos;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MyBatisAdminRoleStore {

    RoleRow findById(Long id);

    List<RoleRow> findAll();

    List<RoleRow> findByUserId(Long userId);

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    class RoleRow {
        private Long id;
        private String roleCode;
        private String roleName;
        private boolean enabled;

        public RoleDtos.RoleResponse toResponse() {
            return new RoleDtos.RoleResponse(id, roleCode, roleName, enabled);
        }

        public RoleDtos.RoleSummary toSummary() {
            return new RoleDtos.RoleSummary(id, roleCode, roleName);
        }
    }
}
