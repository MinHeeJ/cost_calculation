package com.sicost.shared.health;

/** 서비스 프로세스의 응답 가능 상태이며 DB 진단 결과를 의미하지 않는다. */
public record Health(String status) {
}
