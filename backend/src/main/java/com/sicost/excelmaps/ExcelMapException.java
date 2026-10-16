package com.sicost.excelmaps;

/** Safe feature business error; messages never contain persistence details. */
public class ExcelMapException extends RuntimeException {
    private final int status;
    private final String code;
    private final String field;

    public ExcelMapException(int status, String code, String field, String message) {
        super(message);
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
