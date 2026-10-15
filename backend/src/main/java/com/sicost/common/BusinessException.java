package com.sicost.common;

/** 업무 서비스가 검증한 안전한 안내문과 HTTP 상태·업무 코드를 공통 오류 처리기에 전달한다. */
public class BusinessException extends RuntimeException {
    private final int status;
    private final String code;
    private final String field;

    /** message에는 사용자에게 공개 가능한 문구만 전달하며 SQL·내부 예외 메시지는 전달하지 않는다. */
    public BusinessException(int status, String code, String message, String field) {
        super(message);
        if (status != 400 && status != 404 && status != 409) {
            throw new IllegalArgumentException("Unsupported business error status");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Business error code is required");
        }
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }
}
