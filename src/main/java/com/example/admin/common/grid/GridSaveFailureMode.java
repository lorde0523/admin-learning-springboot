package com.example.admin.common.grid;

public enum GridSaveFailureMode {
    /**
     * DB 현재 상태와 맞지 않는 row는 예외로 보지 않고 건너뛰며 메시지로 반환합니다.
     */
    SKIP_AND_MESSAGE,

    /**
     * DB 현재 상태와 맞지 않는 row가 하나라도 있으면 예외를 던져 전체 저장을 중단합니다.
     */
    STRICT_EXCEPTION
}
