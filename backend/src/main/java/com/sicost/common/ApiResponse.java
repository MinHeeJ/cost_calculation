package com.sicost.common;

/** 조회 결과를 한 번만 감싸는 공통 성공 봉투이다. */
public record ApiResponse<T>(boolean success, T data, Meta meta) {
    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return new ApiResponse<>(true, data, new Meta(requestId));
    }
}
