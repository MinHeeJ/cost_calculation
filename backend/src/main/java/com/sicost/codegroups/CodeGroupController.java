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
import org.springframework.web.bind.annotation.RestController;

/** 인증 없는 UI-27 관리 API. 조회 봉투와 본문 없는 command 응답을 분리한다. */
@RestController
public class CodeGroupController {
    private final CodeGroupService service;

    public CodeGroupController(CodeGroupService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/code-groups")
    public ApiResponse<List<CodeGroupRow>> getCodeGroups(HttpServletRequest request) {
        return ApiResponse.ok(service.getCodeGroups(), RequestIdFilter.requestId(request));
    }

    @GetMapping("/api/admin/code-groups/{group}/codes")
    public ApiResponse<List<AdminCodeRow>> getAdminCodes(
        @PathVariable String group, HttpServletRequest request
    ) {
        return ApiResponse.ok(service.getAdminCodes(group), RequestIdFilter.requestId(request));
    }

    @PostMapping("/api/admin/code-groups")
    public ResponseEntity<Void> createCodeGroup(@RequestBody JsonNode body) {
        service.createCodeGroup(body);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/code-groups/{group}/codes")
    public ResponseEntity<Void> saveCodes(@PathVariable String group, @RequestBody JsonNode body) {
        service.saveCodes(group, body);
        return ResponseEntity.noContent().build();
    }
}
