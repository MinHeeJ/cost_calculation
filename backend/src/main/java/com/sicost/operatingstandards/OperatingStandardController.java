package com.sicost.operatingstandards;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sicost.common.ApiResponse;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 인증 없는 기준값 조회/적용월 저장을 실제 영속 서비스에 연결한다. */
@RestController
@RequestMapping("/api/admin/operating-standards")
public class OperatingStandardController {
    private final OperatingStandardService service;

    public OperatingStandardController(OperatingStandardService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<OperatingStandardService.CurrentStandards> getOperatingStandards(HttpServletRequest request) {
        return ApiResponse.ok(service.getOperatingStandards(), RequestIdFilter.requestId(request));
    }

    @GetMapping("/{oprt_std_id}/history")
    public ApiResponse<List<OperatingStandardView>> getOperatingStandardHistory(
        @PathVariable("oprt_std_id") String key, HttpServletRequest request
    ) {
        return ApiResponse.ok(service.getOperatingStandardHistory(key), RequestIdFilter.requestId(request));
    }

    @PutMapping("/{oprt_std_id}")
    public ResponseEntity<Void> saveOperatingStandard(
        @PathVariable("oprt_std_id") String key, @RequestBody String body
    ) throws JsonProcessingException {
        var reader = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        service.saveOperatingStandard(key, reader.readTree(body));
        return ResponseEntity.noContent().build();
    }
}
