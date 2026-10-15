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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.yaml.snakeyaml.Yaml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CodeLookupContractTest {
    private CodeLookupMapper mapper;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mapper = mock(CodeLookupMapper.class);
        mvc = MockMvcBuilders.standaloneSetup(new CodeLookupController(new CodeLookupService(mapper)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
    }

    @Test
    void anonymousCodesHaveExactlyChoiceFieldsAndOneEnvelope() throws Exception {
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
            .andExpect(jsonPath("$.data[0].attr2").isEmpty())
            .andExpect(jsonPath("$.data[0].attr3").isEmpty()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("data").isArray()).isTrue();
        assertThat(body.path("data").get(0).size()).isEqualTo(5);
        assertThat(body.path("meta").path("requestId").asText())
            .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        verify(mapper).groupExists("JOB");
        verify(mapper).selectChoices("JOB");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void knownGroupWithoutActiveChoicesIsEmptySuccess() throws Exception {
        when(mapper.groupExists("EMPTY")).thenReturn(true);
        when(mapper.selectChoices("EMPTY")).thenReturn(List.of());
        mvc.perform(get("/api/codes/{group}", "EMPTY"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void unknownGroupIs404NotAnEmptySuccess() throws Exception {
        mvc.perform(get("/api/codes/{group}", "MISSING"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("404"));
        verify(mapper).groupExists("MISSING");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void oversizedGroupIs400WithoutDatabaseCall() throws Exception {
        mvc.perform(get("/api/codes/{group}", "A".repeat(31)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("group"));
        verifyNoInteractions(mapper);
    }

    @Test
    void anonymousParameterUsesRequestedMonthAndSnakeCaseWithNullableFields() throws Exception {
        when(mapper.selectParameter("VAT_RATE", "2026-10")).thenReturn(parameter("2000-01", "10.1250"));
        var result = mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "2026-10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.oprt_std_id").value("VAT_RATE"))
            .andExpect(jsonPath("$.data.aply_bgn_ym").value("2000-01"))
            .andExpect(jsonPath("$.data.oprt_std_nm").value("부가세율"))
            .andExpect(jsonPath("$.data.num_val").value(10.125))
            .andExpect(jsonPath("$.data.txt_val").isEmpty())
            .andExpect(jsonPath("$.data.unit_nm").value("%"))
            .andExpect(jsonPath("$.data.use_plc_desc").isEmpty())
            .andExpect(jsonPath("$.data.data").doesNotExist()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("data").size()).isEqualTo(7);
        assertThat(body.path("meta").path("requestId").asText())
            .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        verify(mapper).selectParameter("VAT_RATE", "2026-10");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void requestedFutureMonthIsNotSubstitutedByCurrentMonth() throws Exception {
        when(mapper.selectParameter("VAT_RATE", "2026-11")).thenReturn(parameter("2026-11", "11"));
        mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "2026-11"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.num_val").value(11))
            .andExpect(jsonPath("$.data.aply_bgn_ym").value("2026-11"));
        verify(mapper).selectParameter("VAT_RATE", "2026-11");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void parameterWithoutApplicableRowIs404() throws Exception {
        mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "1999-12"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("404"));
        verify(mapper).selectParameter("VAT_RATE", "1999-12");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void unknownParameterIs404() throws Exception {
        mvc.perform(get("/api/params/{key}", "MISSING").param("month", "2026-10"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void missingMonthIs400MonthWithoutReading() throws Exception {
        mvc.perform(get("/api/params/{key}", "VAT_RATE"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-00", "2026-1", "26-01", "", "2026-10-01"})
    void malformedMonthIs400MonthWithoutReading(String month) throws Exception {
        mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", month))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));
        verifyNoInteractions(mapper);
    }

    @Test
    void parameterKeySupportsFiftyButNotFiftyOneCharacters() throws Exception {
        String key = "A".repeat(50);
        var row = parameter("2026-01", "1");
        row.setKey(key);
        when(mapper.selectParameter(key, "2026-10")).thenReturn(row);
        mvc.perform(get("/api/params/{key}", key).param("month", "2026-10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.oprt_std_id").value(key));
        mvc.perform(get("/api/params/{key}", key + "A").param("month", "2026-10"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("key"));
        verify(mapper).selectParameter(key, "2026-10");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void codesDatabaseFailureIsSanitizedAndCorrelated() throws Exception {
        when(mapper.groupExists("JOB")).thenThrow(new IllegalStateException("SQL jdbc password=private"));
        var result = mvc.perform(get("/api/codes/{group}", "JOB"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
            .andReturn();
        assertSafeError(result.getResponse());
    }

    @Test
    void parameterDatabaseFailureIsSanitizedAndCorrelated() throws Exception {
        when(mapper.selectParameter("VAT_RATE", "2026-10"))
            .thenThrow(new IllegalStateException("SQL jdbc password=private"));
        var result = mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "2026-10"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
            .andReturn();
        assertSafeError(result.getResponse());
    }

    @Test
    @SuppressWarnings("unchecked")
    void durableApprovedContractDeclaresBothPublicOperationsAndResponseFields() throws Exception {
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            Map<String, Object> contract = new Yaml().load(input);
            var paths = (Map<String, Map<String, Map<String, Object>>>) contract.get("paths");
            assertThat(paths.get("/api/codes/{group}").get("get").get("operationId")).isEqualTo("getCodes");
            assertThat((List<?>) paths.get("/api/codes/{group}").get("get").get("security")).isEmpty();
            assertThat(paths.get("/api/params/{key}").get("get").get("operationId")).isEqualTo("getParam");
            assertThat((List<?>) paths.get("/api/params/{key}").get("get").get("security")).isEmpty();
            var components = (Map<String, Object>) contract.get("components");
            var schemas = (Map<String, Map<String, Object>>) components.get("schemas");
            assertThat((List<String>) schemas.get("CodeChoice").get("required"))
                .containsExactlyInAnyOrder("code", "name", "attr1", "attr2", "attr3");
            assertThat((List<String>) schemas.get("OperatingStandardView").get("required"))
                .containsExactlyInAnyOrder(
                    "oprt_std_id", "aply_bgn_ym", "oprt_std_nm", "num_val", "txt_val", "unit_nm", "use_plc_desc"
                );
        }
        anonymousCodesHaveExactlyChoiceFieldsAndOneEnvelope();
        anonymousParameterUsesRequestedMonthAndSnakeCaseWithNullableFields();
    }

    private ParameterRow parameter(String month, String number) {
        var row = new ParameterRow();
        row.setKey("VAT_RATE");
        row.setMonth(month);
        row.setName("부가세율");
        row.setNumber(new BigDecimal(number));
        row.setUnit("%");
        return row;
    }

    private void assertSafeError(org.springframework.mock.web.MockHttpServletResponse response) throws Exception {
        assertThat(response.getContentAsString()).doesNotContain("SQL", "jdbc", "password", "private");
        var body = json.readTree(response.getContentAsByteArray());
        assertThat(body.path("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(body.path("meta").path("requestId").asText()).isEqualTo(body.path("requestId").asText());
    }
}
