package com.example.admin.menu.dto.adminmenu;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuGridRow extends MenuRequest {

    @NotNull
    private Long id;
}
