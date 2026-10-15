package com.sicost.shared.monthclose;

import com.sicost.common.ApiResponse;
import com.sicost.common.DataScope;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 필수 문자열 월 범위를 받아 안전한 검증과 비페이지 조회에 연결한다. */
@RestController
public class MonthCloseController {
    private final MonthCloseService service;

    public MonthCloseController(MonthCloseService service) {
        this.service = service;
    }

    /** CLOSED도 공개 조회 가능하며 마감·해제 명령은 제공하지 않는다. */
    @GetMapping("/api/month-close")
    public ApiResponse<List<MonthClose>> getMonthClose(
        @RequestParam("from") String from,
        @RequestParam("to") String to,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(service.getMonthClose(from, to, DataScope.ALL), RequestIdFilter.requestId(request));
    }
}
