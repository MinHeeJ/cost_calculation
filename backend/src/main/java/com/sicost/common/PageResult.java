package com.sicost.common;

import java.util.List;

/** 후속 일반 목록 전용 타입. 현재 메뉴·월 조회에는 적용하지 않는다. */
public record PageResult<T>(List<T> items, long total, int page, int size, String sort) {
    public PageResult {
        if (page < 1 || (size != 20 && size != 50 && size != 100) || total < 0) {
            throw new IllegalArgumentException("목록 페이지 조건을 확인해 주세요.");
        }
        items = List.copyOf(items);
    }
}
