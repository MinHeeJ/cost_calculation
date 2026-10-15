package com.sicost.shared.monthclose;

/** 저장된 상태 또는 조회 중 확장한 OPEN 월을 전달하는 HTTP DTO이다. */
public record MonthClose(String month, String status) {
}
