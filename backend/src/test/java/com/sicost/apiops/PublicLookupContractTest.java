package com.sicost.apiops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.GlobalExceptionHandler;
import com.sicost.common.RequestIdFilter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicLookupContractTest {
    private PublicLookupMapper mapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mapper = mock(PublicLookupMapper.class);
        var service = new PublicLookupService(mapper);
        mvc = MockMvcBuilders.standaloneSetup(new PublicLookupController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
    }

    @Test
    void publicCodesMatchClasspathContractAndExposeOnlyChoiceFields() throws Exception {
        var row = new CodeChoiceRow();
        row.setCode("BE");
        row.setName("백엔드");
        row.setAttr1("200000");
        when(mapper.groupExists("JOB")).thenReturn(true);
        when(mapper.selectChoices("JOB")).thenReturn(List.of(row));
        var result = mvc.perform(get("/api/codes/{group}", "JOB"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].code").value("BE"))
            .andExpect(jsonPath("$.data[0].name").value("백엔드"))
            .andExpect(jsonPath("$.data[0].attr1").value("200000"))
            .andReturn();
        var data = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray()).path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.get(0).size()).isEqualTo(5);
        assertThat(data.get(0).get("attr2").isNull()).isTrue();
        assertThat(data.get(0).get("attr3").isNull()).isTrue();
        assertRequestId(result);
        assertOperation("/api/codes/{group}", "getCodes");
        verify(mapper).groupExists("JOB");
        verify(mapper).selectChoices("JOB");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void existingGroupWithNoActiveChoicesReturnsEmptyArray() throws Exception {
        when(mapper.groupExists("JOB")).thenReturn(true);
        when(mapper.selectChoices("JOB")).thenReturn(List.of());
        mvc.perform(get("/api/codes/{group}", "JOB"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void unknownGroupReturns404WithoutReadingChoices() throws Exception {
        var result = mvc.perform(get("/api/codes/{group}", "UNKNOWN"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("404"))
            .andExpect(jsonPath("$.success").value(false)).andReturn();
        assertRequestId(result);
        verify(mapper).groupExists("UNKNOWN");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void overlongGroupIs400WithoutPersistenceAccess() throws Exception {
        mvc.perform(get("/api/codes/{group}", "A".repeat(31)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("group"));
        verifyNoInteractions(mapper);
    }

    @Test
    void publicParameterMatchesContractAndPreservesZeroAndNulls() throws Exception {
        var row = new ParameterLookupRow();
        row.setOprtStdId("VAT_RATE");
        row.setAplyBgnYm("2000-01");
        row.setOprtStdNm("부가세율");
        row.setNumVal(BigDecimal.ZERO);
        row.setUnitNm("%");
        when(mapper.selectParameter("VAT_RATE", "2026-10")).thenReturn(row);
        var result = mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "2026-10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.oprt_std_id").value("VAT_RATE"))
            .andExpect(jsonPath("$.data.aply_bgn_ym").value("2000-01"))
            .andExpect(jsonPath("$.data.num_val").value(0))
            .andExpect(jsonPath("$.data.unit_nm").value("%"))
            .andReturn();
        var data = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray()).path("data");
        assertThat(data.size()).isEqualTo(7);
        assertThat(data.get("txt_val").isNull()).isTrue();
        assertThat(data.get("use_plc_desc").isNull()).isTrue();
        assertRequestId(result);
        assertOperation("/api/params/{key}", "getParam");
        verify(mapper).selectParameter("VAT_RATE", "2026-10");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void missingMonthIs400WithoutPersistenceAccess() throws Exception {
        mvc.perform(get("/api/params/{key}", "VAT_RATE"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-00", "2026-1", "26-10", "2026-10-01", ""})
    void malformedMonthIs400WithoutPersistenceAccess(String month) throws Exception {
        mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", month))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));
        verifyNoInteractions(mapper);
    }

    @Test
    void overlongParameterKeyIs400WithoutPersistenceAccess() throws Exception {
        mvc.perform(get("/api/params/{key}", "A".repeat(51)).param("month", "2026-10"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("key"));
        verifyNoInteractions(mapper);
    }

    @Test
    void absentApplicableParameterIs404AndDoesNotCreateAnything() throws Exception {
        var result = mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "1999-12"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("404")).andReturn();
        assertRequestId(result);
        verify(mapper).selectParameter("VAT_RATE", "1999-12");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void codeStorageFailureUsesSafeErrorEnvelope() throws Exception {
        when(mapper.groupExists("JOB")).thenThrow(new IllegalStateException("private SQL connection details"));
        var result = mvc.perform(get("/api/codes/{group}", "JOB"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("500"))
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQL", "private", "connection");
        assertRequestId(result);
    }

    @Test
    void parameterStorageFailureUsesSafeErrorEnvelope() throws Exception {
        when(mapper.selectParameter("VAT_RATE", "2026-10"))
            .thenThrow(new IllegalStateException("private SQL connection details"));
        var result = mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "2026-10"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQL", "private", "connection");
        assertRequestId(result);
    }

    private void assertRequestId(MvcResult result) throws Exception {
        var json = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
        var id = result.getResponse().getHeader("X-Request-Id");
        assertThat(id).isNotBlank();
        assertThat(json.path("meta").path("requestId").asText()).isEqualTo(id);
        if (!json.path("success").asBoolean()) {
            assertThat(json.path("requestId").asText()).isEqualTo(id);
        }
    }

    @SuppressWarnings("unchecked")
    private void assertOperation(String path, String operationId) throws Exception {
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            Map<String, Object> contract = new Yaml().load(input);
            var paths = (Map<String, Object>) contract.get("paths");
            var operation = (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get("get");
            assertThat(operation.get("operationId")).isEqualTo(operationId);
            assertThat((List<?>) operation.get("security")).isEmpty();
            assertThat((Map<String, ?>) operation.get("responses")).containsKeys("200", "400", "404", "500");
        }
    }
}
