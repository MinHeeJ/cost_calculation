package com.sicost.adminops;

import com.sicost.common.ApiResponse;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UI-27의 공개 읽기 전용 변경 이력을 기존 응답·추적 봉투로 제공한다. */
@RestController
public class CodeHistoryController {
    private final CodeHistoryService service;

    public CodeHistoryController(CodeHistoryService service) {
        this.service = service;
    }

    /** 인증·역할을 요구하지 않으며 이력 수정/삭제 endpoint는 제공하지 않는다. */
    @GetMapping("/api/admin/code-history")
    public ApiResponse<CodeHistoryPage> getCodeHistory(
        @RequestParam(value = "cd_grp_id", required = false) String group,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "50") int size,
        @RequestParam(value = "sort", defaultValue = "chg_at,desc") String sort,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(service.getCodeHistory(group, page, size, sort), RequestIdFilter.requestId(request));
    }
}
