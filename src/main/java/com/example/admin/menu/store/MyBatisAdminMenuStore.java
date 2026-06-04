package com.example.admin.menu.store;

import com.example.admin.menu.dto.adminmenu.MenuResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MyBatisAdminMenuStore {

    MenuRow findById(Long id);

    List<MenuRow> search(@Param("nameKeyword") String nameKeyword);

    List<MenuRow> searchPage(
            @Param("nameKeyword") String nameKeyword,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countSearch(@Param("nameKeyword") String nameKeyword);

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

        public MenuResponse toResponse() {
            return new MenuResponse(
                    id, menuCode, menuName, parentMenuId, sortOrder, enabled, createdBy, createdAt);
        }
    }
}
