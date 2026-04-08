package dev.common.dto;

import lombok.Builder;
import lombok.Getter;

// 모든 API 응답을 { success, data, message } 형태로 통일
@Getter
@Builder
public class ApiResponse<T> {

    private static final String DEFAULT_SUCCESS_MESSAGE = "요청에 성공하였습니다.";
    private final Status status;
    private final T data;
    private final String message;

    public static ApiResponse<Void> successEmpty() {
        return ApiResponse.<Void>builder()
                .status(Status.SUCCESS)
                .data(null)
                .message(DEFAULT_SUCCESS_MESSAGE)
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(Status.SUCCESS)
                .data(data)
                .message(DEFAULT_SUCCESS_MESSAGE)
                .build();
    }

    public static ApiResponse<Void> failure(String message) {
        return ApiResponse.<Void>builder()
                .status(Status.FAIL)
                .data(null)
                .message(message)
                .build();
    }

    public enum Status {
        SUCCESS, FAIL
    }
}
