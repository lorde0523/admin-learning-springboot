package com.example.admin.menu.dto.adminmenu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
public class MenuGridSaveRequest {

    private List<@NotNull @Valid MenuRequest> createdRows = new ArrayList<>();
    private List<@NotNull @Valid MenuGridRow> updatedRows = new ArrayList<>();
    private List<@NotNull Long> deletedIds = new ArrayList<>();
}
