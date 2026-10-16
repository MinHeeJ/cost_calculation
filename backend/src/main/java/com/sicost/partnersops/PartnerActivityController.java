package com.sicost.partnersops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.ApiResponse;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** Public UI-15 partner activity endpoints; business invariants live in the transactional service. */
@RestController
public class PartnerActivityController {
    private final PartnerActivityService service;

    public PartnerActivityController(PartnerActivityService service) {
        this.service = service;
    }

    @GetMapping("/api/partners/options")
    public ApiResponse<?> getPartnerOptions(@RequestParam("kind") String kind, HttpServletRequest request) {
        return ApiResponse.ok(service.getPartnerOptions(kind), RequestIdFilter.requestId(request));
    }

    @GetMapping("/api/partners/{id}/assignments")
    public ApiResponse<?> getPartnerAssignments(@PathVariable("id") long id, HttpServletRequest request) {
        return ApiResponse.ok(service.getPartnerAssignments(id), RequestIdFilter.requestId(request));
    }

    @GetMapping("/api/partners/{id}/history")
    public ApiResponse<?> getPartnerHistory(@PathVariable("id") long id, HttpServletRequest request) {
        return ApiResponse.ok(service.getPartnerHistory(id), RequestIdFilter.requestId(request));
    }

    @PostMapping("/api/partners/disable")
    public ApiResponse<?> disablePartners(@RequestBody JsonNode body, HttpServletRequest request) {
        return ApiResponse.ok(service.disablePartners(body), RequestIdFilter.requestId(request));
    }

    @PostMapping("/api/partners/{id}/enable")
    public ApiResponse<?> enablePartner(@PathVariable("id") long id, HttpServletRequest request) {
        return ApiResponse.ok(service.enablePartner(id), RequestIdFilter.requestId(request));
    }
}
