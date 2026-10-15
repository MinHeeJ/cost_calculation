package com.sicost.common;

import java.util.List;
import org.springframework.stereotype.Component;

/** 인증 도입 전의 고정 사용자 자리이며 DB 계정이나 로그인으로 해석하지 않는다. */
@Component
public class CurrentUser {
    public Long userId() {
        return null;
    }

    public String name() {
        return "기본 사용자";
    }

    public List<Long> roleIds() {
        return List.of(1L);
    }

    public DataScope dataScope() {
        return DataScope.ALL;
    }
}
