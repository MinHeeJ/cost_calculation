package com.sicost.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 공통 advice의 HTTP 동작을 검증한다. 업무별 저장·SQL·트랜잭션 검증은 해당 기능의 책임이다. */
class BusinessErrorContractTest {
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new BusinessErrorController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();
    }

    @RestController
    static class BusinessErrorController {
        @GetMapping("/contract/business/{code}")
        void conflict(@PathVariable String code) {
            throw new BusinessException(409, code, "업무 조건을 확인해 주세요.", null);
        }

        @GetMapping("/contract/business-missing")
        void missing() {
            throw new BusinessException(404, "404", "대상이 없습니다.", null);
        }

        @GetMapping("/contract/business-invalid")
        void invalid() {
            throw new ValidationException("dtl_cd", "코드를 입력하세요.");
        }

        @GetMapping("/contract/business-failure")
        void failure() {
            throw new IllegalStateException("SQL jdbc internal stacktrace");
        }

        @PostMapping("/contract/business-body")
        ResponseEntity<Void> save(@RequestBody Input input) {
            return ResponseEntity.noContent().build();
        }
    }

    record Input(Integer sort_seq) { }

    @ParameterizedTest
    @CsvSource({"DUPLICATE_CODE", "SYSTEM_CODE_LOCKED", "CLOSED_MONTH"})
    void businessConflictsKeepExactCodeAndNullableField(String code) throws Exception {
        var result = mvc.perform(get("/contract/business/{code}", code)
                .header("X-Request-Id", "business-123"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").value("업무 조건을 확인해 주세요."))
            .andReturn();
        assertErrorEnvelope(result);
        assertThat(json.readTree(result.getResponse().getContentAsByteArray()).get("field").isNull()).isTrue();
    }

    @Test
    void missingBusinessObjectIs404Not500() throws Exception {
        assertErrorEnvelope(mvc.perform(get("/contract/business-missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("404"))
            .andExpect(jsonPath("$.message").value("대상이 없습니다."))
            .andReturn());
    }

    @Test
    void existingValidationKeepsFieldMessageAndNumericCode() throws Exception {
        assertErrorEnvelope(mvc.perform(get("/contract/business-invalid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("400"))
            .andExpect(jsonPath("$.field").value("dtl_cd"))
            .andExpect(jsonPath("$.message").value("코드를 입력하세요."))
            .andReturn());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{", "{\"sort_seq\":\"private-invalid-value\"}", ""})
    void malformedOrMissingJsonIs400WithoutParserLeakage(String payload) throws Exception {
        var result = mvc.perform(post("/contract/business-body")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload == null ? "" : payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("400"))
            .andExpect(jsonPath("$.field").value("body"))
            .andReturn();
        assertErrorEnvelope(result);
        assertThat(result.getResponse().getContentAsString())
            .doesNotContain("private-invalid-value", "Jackson", "Integer", "com.sicost");
    }

    @Test
    void anonymousSuccessfulCommandHasZeroBytesAndRequestId() throws Exception {
        mvc.perform(post("/contract/business-body")
                .header("X-Request-Id", "command-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sort_seq\":1}"))
            .andExpect(status().isNoContent())
            .andExpect(header().string("X-Request-Id", "command-123"))
            .andExpect(content().bytes(new byte[0]));
    }

    @Test
    void unexpectedFailureRemainsSanitized500() throws Exception {
        var result = mvc.perform(get("/contract/business-failure"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("500"))
            .andReturn();
        assertErrorEnvelope(result);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQL", "jdbc", "stacktrace");
    }

    @Test
    void errorFactoryPreservesOldAndNewCallers() {
        assertThat(ApiError.of(400, "입력 오류", "dtl_cd", "id"))
            .isEqualTo(ApiError.of("400", "입력 오류", "dtl_cd", "id"));
        assertThatThrownBy(() -> new BusinessException(401, "401", "인증", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BusinessException(409, "", "오류", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertErrorEnvelope(MvcResult result) throws Exception {
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(id).isNotBlank();
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("requestId").asText()).isEqualTo(id);
        assertThat(body.get("meta").get("requestId").asText()).isEqualTo(id);
        assertThat(body.has("data")).isFalse();
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            Map<String, Object> contract = new org.yaml.snakeyaml.Yaml().load(input);
            var components = (Map<?, ?>) contract.get("components");
            var schemas = (Map<?, ?>) components.get("schemas");
            var schema = (Map<?, ?>) schemas.get("ApiError");
            for (Object required : (Iterable<?>) schema.get("required")) {
                assertThat(body.has(required.toString())).as(required.toString()).isTrue();
            }
        }
    }
}
