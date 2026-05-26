package com.example.admin.menu.dto.adminmenu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuBulkRequest {

    @NotEmpty
    private List<@Valid MenuRequest> menus = new ArrayList<>();
}
