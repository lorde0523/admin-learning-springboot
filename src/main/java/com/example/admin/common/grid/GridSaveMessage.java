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

    /**
     * 메시지가 발생한 작업 구분입니다. 예: CREATE, UPDATE, DELETE.
     */
    private String operation;

    /**
     * 해당 row의 처리 결과입니다. 예: SKIPPED, WARNING, ERROR.
     */
    private String result;

    /**
     * 클라이언트가 row를 식별할 수 있는 key 문자열입니다. 단일 ID 또는 복합 ID 문자열이 들어갑니다.
     */
    private String key;

    /**
     * 사용자 또는 클라이언트에 전달할 상세 메시지입니다.
     */
    private String message;
}
