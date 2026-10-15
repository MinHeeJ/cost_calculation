package com.sicost.operatingstandards;

import com.sicost.common.ApiResponse;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 인증 없는 기준값 관리 계약을 실제 서비스에 연결한다. */
@RestController
public class OperatingStandardController {
    private final OperatingStandardService service;

    public OperatingStandardController(OperatingStandardService service) {
        this.service = service;
    }

    /** 전체 현재값과 마지막 마감월을 한 번만 공통 봉투로 감싼다. */
    @GetMapping("/api/admin/operating-standards")
    public ApiResponse<OperatingStandardsResult> getOperatingStandards(HttpServletRequest request) {
        return ApiResponse.ok(service.getOperatingStandards(), RequestIdFilter.requestId(request));
    }

    /** 전체 적용 이력은 미래월도 포함하며 페이지 절단하지 않는다. */
    @GetMapping("/api/admin/operating-standards/{oprt_std_id}/history")
    public ApiResponse<List<OperatingStandardView>> getOperatingStandardHistory(
        @PathVariable("oprt_std_id") String key,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(service.getOperatingStandardHistory(key), RequestIdFilter.requestId(request));
    }

    /** 완료는 bodyless 204이며 요청 추적 헤더는 기존 필터가 유지한다. */
    @PutMapping("/api/admin/operating-standards/{oprt_std_id}")
    public ResponseEntity<Void> saveOperatingStandard(
        @PathVariable("oprt_std_id") String key,
        @RequestBody SaveOperatingStandardRequest body
    ) {
        service.saveOperatingStandard(key, body);
        return ResponseEntity.noContent().build();
    }
}
