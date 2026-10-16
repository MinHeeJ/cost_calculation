package com.sicost.apiops;

import com.sicost.common.ValidationException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 무인증 선택지와 요청월 기준값을 읽기 전용 트랜잭션으로 제공한다. */
@Service
@Transactional(readOnly = true)
public class PublicLookupService {
    private final PublicLookupMapper mapper;

    public PublicLookupService(PublicLookupMapper mapper) {
        this.mapper = mapper;
    }

    /** 비활성 상세코드만 제외하며 빈 기존 그룹은 빈 배열로 유지한다. */
    public List<CodeChoiceRow> getCodes(String group) {
        validateKey(group, "group", 30);
        if (!mapper.groupExists(group)) {
            throw new LookupNotFoundException();
        }
        return mapper.selectChoices(group);
    }

    /** 서버 현재월과 무관하게 요청월 이하의 마지막 적용값만 반환한다. */
    public ParameterLookupRow getParam(String key, String month) {
        validateKey(key, "key", 50);
        if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new ValidationException("month", "YYYY-MM 형식으로 입력해 주세요.");
        }
        var row = mapper.selectParameter(key, month);
        if (row == null) {
            throw new LookupNotFoundException();
        }
        return row;
    }

    private void validateKey(String value, String field, int length) {
        if (value == null || value.isEmpty() || value.length() > length) {
            throw new ValidationException(field, "식별자의 길이를 확인해 주세요.");
        }
    }
}
