package com.example.admin.menu.dto.adminmenu;

import com.example.admin.common.grid.GridSaveMessage;
import java.util.List;
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
    private List<GridSaveMessage> messages;
}
