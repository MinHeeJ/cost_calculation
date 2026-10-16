package com.sicost.codegroups;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.ApiResponse;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 공통코드 관리: GET 봉투는 한 번만, 성공한 쓰기는 본문 없는 204로 응답한다. */
@RestController
public class CodeGroupController {
    private final CodeGroupService service;

    public CodeGroupController(CodeGroupService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/code-groups")
    public ApiResponse<List<CodeGroupRow>> getCodeGroups(HttpServletRequest request) {
        return ApiResponse.ok(service.groups(), RequestIdFilter.requestId(request));
    }

    @PostMapping("/api/admin/code-groups")
    public ResponseEntity<Void> createCodeGroup(@RequestBody JsonNode body) {
        service.createGroup(body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/code-groups/{group}/codes")
    public ApiResponse<List<CodeDetailRow>> getAdminCodes(
        @PathVariable("group") String group, HttpServletRequest request
    ) {
        return ApiResponse.ok(service.codes(group), RequestIdFilter.requestId(request));
    }

    @PutMapping("/api/admin/code-groups/{group}/codes")
    public ResponseEntity<Void> saveCodes(@PathVariable("group") String group, @RequestBody JsonNode body) {
        service.saveCodes(group, body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/code-history")
    public ApiResponse<CodeGroupService.HistoryPage> getCodeHistory(
        @RequestParam(value = "cd_grp_id", required = false) String group,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "50") int size,
        @RequestParam(value = "sort", defaultValue = "chg_at,desc") String sort,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(service.historyPage(group, page, size, sort), RequestIdFilter.requestId(request));
    }
}
