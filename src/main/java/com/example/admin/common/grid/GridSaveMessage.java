package com.example.admin.common.grid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridSaveMessage {

    private String operation;
    private String result;
    private String key;
    private String message;
}
