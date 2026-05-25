package com.example.admin.menu.mapper;

import com.example.admin.menu.dto.MenuDtos;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMenuMapper {

    MenuRow findById(Long id);

    List<MenuRow> search(@Param("nameKeyword") String nameKeyword);

    List<MenuRow> children(Long parentMenuId);

    List<MenuRow> findByRoleId(Long roleId);

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    class MenuRow {
        private Long id;
        private String menuCode;
        private String menuName;
        private Long parentMenuId;
        private int sortOrder;
        private boolean enabled;
        private String createdBy;
        private LocalDateTime createdAt;

        public MenuDtos.MenuResponse toResponse() {
            return new MenuDtos.MenuResponse(
                    id, menuCode, menuName, parentMenuId, sortOrder, enabled, createdBy, createdAt);
        }
    }
}

