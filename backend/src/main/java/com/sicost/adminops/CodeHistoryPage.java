package com.sicost.adminops;

import java.util.List;

/** 승인된 네 필드만 노출하며 외부 sort는 응답에 추가하지 않는다. */
public record CodeHistoryPage(List<CodeHistoryView> items, long total, int page, int size) {
}
