package com.sicost.operatingstandards;

/** 공개 기준값의 안전한 업무 오류만 운반한다. */
public class OperatingStandardException extends RuntimeException {
    final int status;
    final String code;
    final String field;

    public OperatingStandardException(int status, String code, String field, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }
}
