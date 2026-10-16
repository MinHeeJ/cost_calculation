package com.sicost.apiops;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.sicost.common.ApiError;
import com.sicost.common.ApiResponse;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기존 공통 봉투와 추적 ID를 유지하는 공개 선택지/적용월 조회 경계이다. */
@RestController
public class PublicLookupController {
    private final PublicLookupService service;

    public PublicLookupController(PublicLookupService service) {
        this.service = service;
    }

    /** 인증 없이 활성 선택지를 한 번만 감싸서 반환한다. */
    @GetMapping("/api/codes/{group}")
    public ApiResponse<List<CodeChoice>> getCodes(
        @PathVariable("group") String group,
        HttpServletRequest request
    ) {
        var choices = service.getCodes(group).stream()
            .map(row -> new CodeChoice(row.getCode(), row.getName(), row.getAttr1(), row.getAttr2(), row.getAttr3()))
            .toList();
        return ApiResponse.ok(choices, RequestIdFilter.requestId(request));
    }

    /** 필수 요청월 기준으로 값을 읽으며 새 행을 생성하지 않는다. */
    @GetMapping("/api/params/{key}")
    public ApiResponse<ParameterView> getParam(
        @PathVariable("key") String key,
        @RequestParam("month") String month,
        HttpServletRequest request
    ) {
        var row = service.getParam(key, month);
        var view = new ParameterView(
            row.getOprtStdId(), row.getAplyBgnYm(), row.getOprtStdNm(), row.getNumVal(),
            row.getTxtVal(), row.getUnitNm(), row.getUsePlcDesc()
        );
        return ApiResponse.ok(view, RequestIdFilter.requestId(request));
    }

    /** 기능 한정 not-found를 기존 오류 봉투로 표현하여 공통 handler와 충돌하지 않는다. */
    @ExceptionHandler(LookupNotFoundException.class)
    public ResponseEntity<ApiError> notFound(LookupNotFoundException error, HttpServletRequest request) {
        return ResponseEntity.status(404)
            .body(ApiError.of(404, error.getMessage(), null, RequestIdFilter.requestId(request)));
    }

    /** 선택 목록에는 저장소 내부 필드와 감사 정보를 노출하지 않는다. */
    public record CodeChoice(String code, String name, String attr1, String attr2, String attr3) {
    }

    /** 신규 wire에만 snake_case를 적용해 기존 API의 camelCase를 보존한다. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ParameterView(
        String oprtStdId,
        String aplyBgnYm,
        String oprtStdNm,
        BigDecimal numVal,
        String txtVal,
        String unitNm,
        String usePlcDesc
    ) {
    }
}
