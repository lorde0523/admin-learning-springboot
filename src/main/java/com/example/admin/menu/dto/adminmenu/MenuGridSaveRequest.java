package com.example.admin.menu.dto.adminmenu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

    @Size(max = 1000)
    private List<@NotNull @Valid MenuGridRow> createdRows = new ArrayList<>();

    @Size(max = 1000)
    private List<@NotNull @Valid MenuGridRow> updatedRows = new ArrayList<>();

    @Size(max = 1000)
    private List<@NotNull @Positive Long> deletedIds = new ArrayList<>();
}
