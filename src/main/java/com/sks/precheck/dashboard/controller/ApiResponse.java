package com.sks.precheck.dashboard.controller;

import lombok.Getter;

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

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "");
    }

    public static <T> ApiResponse<T> fail(String message) {
        if (message == null || message.isBlank()) {
            return new ApiResponse<>(false, null, "ERROR");
        }
        return new ApiResponse<>(false, null, message);
    }
}
