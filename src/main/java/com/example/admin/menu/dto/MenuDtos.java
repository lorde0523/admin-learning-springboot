package com.example.admin.menu.dto;

import com.example.admin.menu.entity.AdminMenu;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class MenuDtos {

    private MenuDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuRequest {
        @NotBlank
        private String menuCode;

        @NotBlank
        private String menuName;

        private Long parentMenuId;

        @PositiveOrZero
        private int sortOrder;

        @NotNull
        private Boolean enabled;

        public String menuCode() {
            return menuCode;
        }

        public String menuName() {
            return menuName;
        }

        public Long parentMenuId() {
            return parentMenuId;
        }

        public int sortOrder() {
            return sortOrder;
        }

        public Boolean enabled() {
            return enabled;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuGridRow extends MenuRequest {
        @NotNull
        private Long id;

        public Long id() {
            return id;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuResponse {
        private Long id;
        private String menuCode;
        private String menuName;
        private Long parentMenuId;
        private int sortOrder;
        private boolean enabled;
        private String createdBy;
        private LocalDateTime createdAt;

        public static MenuResponse from(AdminMenu menu) {
            return MenuResponse.builder()
                    .id(menu.getId())
                    .menuCode(menu.getMenuCode())
                    .menuName(menu.getMenuName())
                    .parentMenuId(menu.getParentMenuId())
                    .sortOrder(menu.getSortOrder())
                    .enabled(menu.isEnabled())
                    .createdBy(menu.getCreatedBy())
                    .createdAt(menu.getCreatedAt())
                    .build();
        }

        public Long id() {
            return id;
        }

        public String menuCode() {
            return menuCode;
        }

        public String menuName() {
            return menuName;
        }

        public Long parentMenuId() {
            return parentMenuId;
        }

        public int sortOrder() {
            return sortOrder;
        }

        public boolean enabled() {
            return enabled;
        }

        public String createdBy() {
            return createdBy;
        }

        public LocalDateTime createdAt() {
            return createdAt;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuBulkRequest {
        @NotEmpty
        private List<@Valid MenuRequest> menus = new ArrayList<>();

        public List<MenuRequest> menus() {
            return menus;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuGridSaveRequest {
        private List<@NotNull @Valid MenuRequest> createdRows = new ArrayList<>();
        private List<@NotNull @Valid MenuGridRow> updatedRows = new ArrayList<>();
        private List<@NotNull Long> deletedIds = new ArrayList<>();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GridSaveResponse {
        private int createdCount;
        private int updatedCount;
        private int deletedCount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkInsertResponse {
        private int insertedCount;

        public int insertedCount() {
            return insertedCount;
        }
    }
}
