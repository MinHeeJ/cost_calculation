package com.sicost.excelmaps;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 새 요청에만 엄격한 필드 검증을 적용하며 생략과 명시 null을 구별한다. */
public abstract class ExcelMapInput {
    private final Map<String, JsonNode> values = new LinkedHashMap<>();

    @JsonAnySetter
    public void put(String field, JsonNode value) {
        values.put(field, value);
    }

    public boolean has(String field) {
        return values.containsKey(field);
    }

    public void allowed(Set<String> allowed) {
        for (String field : values.keySet()) {
            if (!allowed.contains(field)) {
                throw new ValidationException(field, "변경할 수 없는 항목입니다.");
            }
        }
        if (values.isEmpty()) {
            throw new ValidationException("body", "변경할 항목을 입력하세요.");
        }
    }

    public String text(String field, int max, boolean required, String message) {
        JsonNode node = values.get(field);
        if (node == null || node.isNull()) {
            if (required) {
                throw new ValidationException(field, message);
            }
            return null;
        }
        if (!node.isTextual()) {
            throw new ValidationException(field, "문자열을 입력하세요.");
        }
        String value = node.textValue();
        if ((required && value.isBlank()) || value.codePointCount(0, value.length()) > max) {
            throw new ValidationException(field, message);
        }
        return value;
    }
}
