package com.sicost.apiops;

import com.sicost.common.BusinessException;
import com.sicost.common.ValidationException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 무인증 소비 API의 입력과 존재 여부를 검증하며 조회로 감사값이나 이력을 변경하지 않는다. */
@Service
@Transactional(readOnly = true)
public class CodeLookupService {
    private final CodeLookupMapper mapper;

    public CodeLookupService(CodeLookupMapper mapper) {
        this.mapper = mapper;
    }

    /** 그룹은 존재하되 사용 가능한 코드가 없으면 빈 전체 목록을 반환한다. */
    public List<CodeChoice> getCodes(String group) {
        validateKey(group, 30, "group");
        if (!mapper.groupExists(group)) {
            throw new BusinessException(404, "404", "코드 그룹을 찾을 수 없습니다.", "group");
        }
        return mapper.selectChoices(group).stream()
            .map(row -> new CodeChoice(
                row.getCode(), row.getName(), row.getAttr1(), row.getAttr2(), row.getAttr3()
            ))
            .toList();
    }

    /** 미래 적용 행을 앞당겨 사용하거나 없는 적용월을 생성하지 않는다. */
    public ParameterView getParam(String key, String month) {
        validateKey(key, 50, "key");
        if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new ValidationException("month", "YYYY-MM 형식으로 입력해 주세요.");
        }
        ParameterRow row = mapper.selectParameter(key, month);
        if (row == null) {
            throw new BusinessException(404, "404", "요청 월에 적용되는 기준값이 없습니다.", "key");
        }
        return new ParameterView(
            row.getKey(), row.getMonth(), row.getName(), row.getNumber(),
            row.getText(), row.getUnit(), row.getUsage()
        );
    }

    private void validateKey(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new ValidationException(field, "식별자의 길이를 확인해 주세요.");
        }
    }
}
