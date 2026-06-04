package com.example.admin.common.grid;

import java.util.List;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridSaveResult {

    /**
     * 신규 등록에 성공한 row 수입니다. 이미 등록되어 skip된 row는 포함하지 않습니다.
     */
    private int createdCount;

    /**
     * 수정에 성공한 row 수입니다.
     */
    private int updatedCount;

    /**
     * 삭제에 성공한 key 수입니다.
     */
    private int deletedCount;

    /**
     * 예외는 아니지만 클라이언트가 후속 판단을 해야 하는 row 단위 처리 메시지입니다.
     */
    private List<GridSaveMessage> messages;

    public static GridSaveResult empty() {
        return GridSaveResult.builder()
                .createdCount(0)
                .updatedCount(0)
                .deletedCount(0)
                .messages(List.of())
                .build();
    }

    public GridSaveResult merge(GridSaveResult other) {
        if (other == null) {
            return this;
        }

        return GridSaveResult.builder()
                .createdCount(createdCount + other.createdCount)
                .updatedCount(updatedCount + other.updatedCount)
                .deletedCount(deletedCount + other.deletedCount)
                .messages(Stream.of(nullToEmpty(messages), nullToEmpty(other.messages))
                        .flatMap(List::stream)
                        .toList())
                .build();
    }

    private static List<GridSaveMessage> nullToEmpty(List<GridSaveMessage> messages) {
        return messages == null ? List.of() : messages;
    }
}
