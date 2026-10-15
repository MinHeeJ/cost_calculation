package com.sicost.excelmaps;

import com.sicost.common.ApiResponse;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 없이 사용하는 엑셀 표기 대응의 조회·추가·제한 필드 변경 API다. */
@RestController
public class ExcelMapController {
    private final ExcelMapService service;

    public ExcelMapController(ExcelMapService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/excel-maps")
    public ApiResponse<List<ExcelMapView>> getExcelMaps(HttpServletRequest request) {
        return ApiResponse.ok(service.getExcelMaps(), RequestIdFilter.requestId(request));
    }

    /** 저장 성공은 본문 없는 204이며 RequestIdFilter가 추적 헤더를 유지한다. */
    @PostMapping("/api/admin/excel-maps")
    public ResponseEntity<Void> createExcelMap(@RequestBody CreateExcelMapRequest input) {
        service.createExcelMap(input);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/admin/excel-maps/{map_id}")
    public ResponseEntity<Void> updateExcelMap(
        @PathVariable("map_id") long id,
        @RequestBody UpdateExcelMapRequest input
    ) {
        service.updateExcelMap(id, input);
        return ResponseEntity.noContent().build();
    }
}
