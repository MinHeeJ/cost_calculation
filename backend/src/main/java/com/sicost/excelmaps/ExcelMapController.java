package com.sicost.excelmaps;

import com.fasterxml.jackson.databind.JsonNode;
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

/** Public Excel-label mapping management; upload and conversion are intentionally absent. */
@RestController
public class ExcelMapController {
    private final ExcelMapService service;

    public ExcelMapController(ExcelMapService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/excel-maps")
    public ApiResponse<List<ExcelMapRow>> getExcelMaps(HttpServletRequest request) {
        return ApiResponse.ok(service.getExcelMaps(), RequestIdFilter.requestId(request));
    }

    @PostMapping("/api/admin/excel-maps")
    public ResponseEntity<Void> createExcelMap(@RequestBody JsonNode body) {
        service.createExcelMap(body);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/admin/excel-maps/{map_id}")
    public ResponseEntity<Void> updateExcelMap(@PathVariable("map_id") long id, @RequestBody JsonNode body) {
        service.updateExcelMap(id, body);
        return ResponseEntity.noContent().build();
    }
}
