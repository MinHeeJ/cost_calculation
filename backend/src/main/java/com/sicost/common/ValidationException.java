package com.sicost.common;

/** 안전한 사용자 안내와 오류 필드를 전달하는 입력 검증 실패이다. */
public class ValidationException extends RuntimeException {
    private final String field;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
