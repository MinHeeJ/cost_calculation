package com.sicost.partners;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.ApiResponse;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public UI-15 catalog transport; business validation and persistence belong to the service. */
@RestController
@RequestMapping("/api/partners")
public class PartnerCatalogController {
    private final PartnerCatalogService service;

    public PartnerCatalogController(PartnerCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PartnerCatalogService.PartnerPage> getPartners(
        @RequestParam(required = false) String kind,
        @RequestParam(defaultValue = "Y") String useYn,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(defaultValue = "ptnr_nm,asc") String sort,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(service.getPartners(kind, useYn, q, page, size, sort),
            RequestIdFilter.requestId(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getPartner(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(service.getPartner(id), RequestIdFilter.requestId(request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPartner(
        @RequestBody JsonNode body, HttpServletRequest request
    ) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.createPartner(body),
            RequestIdFilter.requestId(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updatePartner(
        @PathVariable long id, @RequestBody JsonNode body, HttpServletRequest request
    ) {
        return ApiResponse.ok(service.updatePartner(id, body), RequestIdFilter.requestId(request));
    }
}
