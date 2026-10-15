package com.sicost.shared.monthclose;

import com.sicost.common.DataScope;
import com.sicost.common.ValidationException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 읽기 트랜잭션 내에서 누락 월을 OPEN으로 확장하며 저장·감사를 변경하지 않는다. */
@Service
public class MonthCloseService {
    private final MonthCloseMapper mapper;

    public MonthCloseService(MonthCloseMapper mapper) {
        this.mapper = mapper;
    }

    /** 네 자리 연도 전체를 허용하며 종료월 이후 plusMonths 호출을 하지 않는다. */
    @Transactional(readOnly = true)
    public List<MonthClose> getMonthClose(String from, String to, DataScope scope) {
        YearMonth start = parse(from, "from");
        YearMonth end = parse(to, "to");
        if (start.isAfter(end)) {
            throw new ValidationException("to", "종료 월은 시작 월 이후여야 합니다.");
        }
        var states = new HashMap<String, String>();
        for (var row : mapper.selectRange(from, to)) {
            states.put(row.getMonth(), row.getStatus());
        }
        var result = new ArrayList<MonthClose>();
        YearMonth month = start;
        while (true) {
            String key = month.toString();
            result.add(new MonthClose(key, states.getOrDefault(key, "OPEN")));
            if (month.equals(end)) {
                break;
            }
            month = month.plusMonths(1);
        }
        return result;
    }

    private YearMonth parse(String value, String field) {
        if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new ValidationException(field, "YYYY-MM 형식으로 입력해 주세요.");
        }
        return YearMonth.of(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(5)));
    }
}
