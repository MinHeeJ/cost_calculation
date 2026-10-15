package com.sicost.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResponseContractTest {
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ResponseController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();
    }

    @RestController
    static class ResponseController {
        @GetMapping("/contract/success")
        ApiResponse<List<String>> success(HttpServletRequest request) {
            return ApiResponse.ok(List.of("value"), RequestIdFilter.requestId(request));
        }

        @GetMapping("/contract/invalid")
        void invalid() {
            throw new ValidationException("from", "시작 월을 확인해 주세요.");
        }

        @GetMapping("/contract/failure")
        void failure() {
            throw new IllegalStateException("SQL password=secret internal stacktrace");
        }
    }

    private void matchingId(MvcResult result) throws Exception {
        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(id).isNotBlank();
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("meta").path("requestId").asText()).isEqualTo(id);
        if (!body.path("success").asBoolean()) {
            assertThat(body.path("requestId").asText()).isEqualTo(id);
            assertThat(body.has("field")).isTrue();
            assertThat(body.has("data")).isFalse();
        }
    }

    @Test
    void successContainsOneDataEnvelopeAndSameHeaderId() throws Exception {
        matchingId(mvc.perform(get("/contract/success").header("X-Request-Id", "client-123"))
            .andExpect(status().isOk()).andExpect(header().string("X-Request-Id", "client-123"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data[0]").value("value"))
            .andReturn());
    }

    @Test
    void validationReturnsDirectErrorWithField() throws Exception {
        matchingId(mvc.perform(get("/contract/invalid")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.code").value("400"))
            .andExpect(jsonPath("$.field").value("from")).andReturn());
    }

    @Test
    void unknownPathReturns404Envelope() throws Exception {
        matchingId(mvc.perform(get("/contract/missing")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("404")).andReturn());
    }

    @Test
    void serverErrorDoesNotLeakSensitiveDetails() throws Exception {
        var result = mvc.perform(get("/contract/failure")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("500")).andReturn();
        matchingId(result);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQL", "password", "stacktrace");
    }

    @Test
    void futureListsKeepServerTotalAndSortAndValidateSupportedSizes() {
        for (int size : new int[] {20, 50, 100}) {
            var page = new PageResult<>(List.of("row"), 118, 1, size, "name,asc");
            assertThat(page.total()).isEqualTo(118);
            assertThat(page.sort()).isEqualTo("name,asc");
            assertThat(page.size()).isEqualTo(size);
        }
        assertThatThrownBy(() -> new PageResult<>(List.of("row"), 118, 1, 30, "name,asc"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approvedContractIsLoadedFromDurableClasspath() throws Exception {
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            var contract = new org.yaml.snakeyaml.Yaml().<java.util.Map<String, Object>>load(input);
            assertThat(contract.get("paths")).isInstanceOf(java.util.Map.class);
            var schemas = (java.util.Map<?, ?>) ((java.util.Map<?, ?>) contract.get("components")).get("schemas");
            var declaration = (java.util.Map<?, ?>) schemas.get("ApiError");
            assertThat((List<?>) declaration.get("required"))
                .extracting(Object::toString).contains("field", "requestId", "meta");
        }
    }
}
