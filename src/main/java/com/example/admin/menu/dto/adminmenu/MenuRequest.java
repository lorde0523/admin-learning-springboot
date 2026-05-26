package com.example.admin.menu.dto.adminmenu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuRequest {

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
