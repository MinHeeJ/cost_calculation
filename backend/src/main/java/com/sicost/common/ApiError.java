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
        return of(String.valueOf(status), message, field, requestId);
    }

    /** HTTP 상태와 별개인 업무 충돌 코드를 기존 오류 봉투에 보존한다. */
    public static ApiError of(String code, String message, String field, String requestId) {
        return new ApiError(false, code, message, field, requestId, new Meta(requestId));
    }
}
