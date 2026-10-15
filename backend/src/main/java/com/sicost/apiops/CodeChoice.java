package com.sicost.apiops;

/** 새 선택 목록 전용 투영이며 저장된 업무의 코드명 조회를 대체하지 않는다. */
public record CodeChoice(String code, String name, String attr1, String attr2, String attr3) {
}
