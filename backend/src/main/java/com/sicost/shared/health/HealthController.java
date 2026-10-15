package com.sicost.shared.health;

import com.sicost.common.ApiResponse;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** DB 호출 없이 프로세스 상태와 요청 추적 번호를 제공한다. */
@RestController
public class HealthController {
    /** 승인된 getHealth 계약의 단일 Health 결과를 반환한다. */
    @GetMapping("/api/health")
    public ApiResponse<Health> getHealth(HttpServletRequest request) {
        return ApiResponse.ok(new Health("UP"), RequestIdFilter.requestId(request));
    }
}
