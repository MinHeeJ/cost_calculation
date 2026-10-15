package com.sicost.apiops;

import com.sicost.common.ApiResponse;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증이나 역할 요구 없이 공통 선택 코드와 적용월 기준값을 제공한다. */
@RestController
public class CodeLookupController {
    private final CodeLookupService service;

    public CodeLookupController(CodeLookupService service) {
        this.service = service;
    }

    /** 신규 선택용 Y 코드 목록을 공통 성공 봉투에 한 번만 담는다. */
    @GetMapping("/api/codes/{group}")
    public ApiResponse<List<CodeChoice>> getCodes(
        @PathVariable("group") String group,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(service.getCodes(group), RequestIdFilter.requestId(request));
    }

    /** month는 필수이며 요청 월을 서버의 현재 월로 대체하지 않는다. */
    @GetMapping("/api/params/{key}")
    public ApiResponse<ParameterView> getParam(
        @PathVariable("key") String key,
        @RequestParam("month") String month,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(service.getParam(key, month), RequestIdFilter.requestId(request));
    }
}
