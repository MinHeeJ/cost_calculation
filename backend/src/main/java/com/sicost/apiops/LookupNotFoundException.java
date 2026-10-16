package com.sicost.apiops;

/** 존재하지 않는 그룹 또는 요청월 적용값을 안전한 404로 구분한다. */
public class LookupNotFoundException extends RuntimeException {
    public LookupNotFoundException() {
        super("요청한 그룹 또는 적용 기준값을 찾을 수 없습니다.");
    }
}
