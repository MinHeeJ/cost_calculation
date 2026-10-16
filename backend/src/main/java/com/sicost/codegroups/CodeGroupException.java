package com.sicost.codegroups;

/** 이 기능의 404 및 충돌 코드를 기존 ApiError 봉투에 전달한다. */
public class CodeGroupException extends RuntimeException {
    private final int status;
    private final String code;
    public CodeGroupException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() {
        return status;
    }
    public String code() {
        return code;
    }
}
