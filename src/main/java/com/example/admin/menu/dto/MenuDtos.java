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
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuGridRow extends MenuRequest {
        @NotNull
        private Long id;
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
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuBulkRequest {
        @NotEmpty
        private List<@Valid MenuRequest> menus = new ArrayList<>();
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
    }
}
