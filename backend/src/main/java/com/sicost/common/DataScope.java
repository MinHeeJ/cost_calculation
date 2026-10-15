package com.sicost.common;

/** 후속 조회 서비스가 받을 범위 계약. 현재 요청에는 ALL만 전달한다. */
public enum DataScope {
    ALL, PART, PROJECT, SELF
}
