package com.example.admin.menu.mapper;

import com.example.admin.menu.dto.MenuDtos;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMenuMapper {

    Long nextId();

    int insert(@Param("id") Long id, @Param("request") MenuDtos.MenuRequest request, @Param("auditor") String auditor);

    int bulkInsert(@Param("menus") List<MenuInsertRow> menus, @Param("auditor") String auditor);

    MenuRow findById(Long id);

    List<MenuRow> search(@Param("nameKeyword") String nameKeyword);

    List<MenuRow> children(Long parentMenuId);

    List<MenuRow> findByRoleId(Long roleId);

    int update(@Param("id") Long id, @Param("request") MenuDtos.MenuRequest request, @Param("auditor") String auditor);

    int delete(Long id);

    record MenuInsertRow(Long id, MenuDtos.MenuRequest request) {
    }

    record MenuRow(
            Long id,
            String menuCode,
            String menuName,
            Long parentMenuId,
            int sortOrder,
            boolean enabled,
            String createdBy,
            LocalDateTime createdAt) {

        public MenuDtos.MenuResponse toResponse() {
            return new MenuDtos.MenuResponse(
                    id, menuCode, menuName, parentMenuId, sortOrder, enabled, createdBy, createdAt);
        }
    }
}

