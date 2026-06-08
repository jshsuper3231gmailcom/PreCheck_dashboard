package com.sks.precheck.dashboard.controller;

import lombok.Getter;

/**
 * Dashboard 조회 API 공통 응답 래퍼.
 *
 * 역할:
 * - 화면이 성공 여부와 데이터, 오류 메시지를 같은 형식으로 해석하게 한다.
 *
 * 설계 이유:
 * - 조회 API가 여러 개여도 프런트엔드 오류 처리 분기를 단순하게 유지하기 위해 공통 형식을 사용한다.
 * - 실패 시 메시지를 비워두지 않고 기본값을 보장해 화면에서 예외 문자열 처리 부담을 줄인다.
 */
@Getter
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final String message;

    private ApiResponse(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    /**
     * 성공 응답을 생성한다.
     *
     * @param data 정상 조회 결과 데이터다.
     * @return 성공 플래그가 설정된 응답이다.
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "");
    }

    /**
     * 실패 응답을 생성한다.
     *
     * 실패/무시 조건:
     * - 메시지가 비어 있으면 화면에 빈 문자열이 내려가지 않도록 기본값 `ERROR`를 사용한다.
     *
     * @param message 조회 실패 사유다.
     * @return 실패 플래그가 설정된 응답이다.
     */
    public static <T> ApiResponse<T> fail(String message) {
        if (message == null || message.isBlank()) {
            return new ApiResponse<>(false, null, "ERROR");
        }
        return new ApiResponse<>(false, null, message);
    }
}
