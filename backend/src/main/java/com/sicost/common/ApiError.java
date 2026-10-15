package com.sicost.common;

/** 성공 봉투에 중첩하지 않는 오류 응답이며 field는 null도 직렬화한다. */
public record ApiError(
    boolean success,
    String code,
    String message,
    String field,
    String requestId,
    Meta meta
) {
    public static ApiError of(int status, String message, String field, String requestId) {
        return new ApiError(false, String.valueOf(status), message, field, requestId, new Meta(requestId));
    }
}
