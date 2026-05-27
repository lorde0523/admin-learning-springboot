package com.example.admin.menu.dto.adminmenu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
    @Size(max = 50)
    private String menuCode;

    @NotBlank
    @Size(max = 100)
    private String menuName;

    private Long parentMenuId;

    @PositiveOrZero
    private int sortOrder;

    @NotNull
    private Boolean enabled;
}
