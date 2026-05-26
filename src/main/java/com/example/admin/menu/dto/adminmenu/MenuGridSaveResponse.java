package com.example.admin.menu.dto.adminmenu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuGridSaveResponse {

    private int createdCount;
    private int updatedCount;
    private int deletedCount;
}
