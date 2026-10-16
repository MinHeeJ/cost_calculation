package com.sicost.operatingstandards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OperatingStandardContractTest {
    private OperatingStandardMapper mapper;
    private MockMvc mvc;
    private OperatingStandardRow original;
    private List<OperatingStandardRow> rows;

    @BeforeEach
    void setUp() {
        mapper = mock(OperatingStandardMapper.class);
        var service = new OperatingStandardService(mapper, new CurrentUser(), new PermissionService());
        mvc = MockMvcBuilders.standaloneSetup(new OperatingStandardController(service))
            .setControllerAdvice(new OperatingStandardAdvice(), new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
        original = row("2000-01", "10");
        rows = new ArrayList<>(List.of(original));
        when(mapper.selectHistory("VAT_RATE")).thenAnswer(call -> rows.stream()
            .sorted(java.util.Comparator.comparing(OperatingStandardRow::getAplyBgnYm).reversed()).toList());
        when(mapper.selectLastClosed()).thenReturn("2026-09");
        when(mapper.upsert(any(), isNull())).thenAnswer(call -> {
            OperatingStandardRow saved = call.getArgument(0);
            rows.removeIf(r -> r.getAplyBgnYm().equals(saved.getAplyBgnYm()));
            rows.add(saved);
            return 1;
        });
    }

    private OperatingStandardRow row(String month, String value) {
        var row = new OperatingStandardRow();
        row.setOprtStdId("VAT_RATE");
        row.setAplyBgnYm(month);
        row.setOprtStdNm("부가세율");
        row.setUnitNm("%");
        row.setUsePlcDesc("계산");
        row.setNumVal(new BigDecimal(value));
        return row;
    }

    @Test
    void publicCurrentListHasSingleTypedEnvelopeAndRequestId() throws Exception {
        String month = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
        when(mapper.selectCurrent(month)).thenReturn(List.of(original));
        var result = mvc.perform(get("/api/admin/operating-standards"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].num_val").value(10))
            .andExpect(jsonPath("$.data.items[0].unit_nm").value("%"))
            .andExpect(jsonPath("$.data.items[0].use_plc_desc").value("계산"))
            .andExpect(jsonPath("$.data.last_closed_ym").value("2026-09"))
            .andExpect(jsonPath("$.data.data").doesNotExist()).andReturn();
        var json = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
        assertThat(json.path("meta").path("requestId").asText())
            .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        verify(mapper).selectCurrent(month);
        verify(mapper).selectLastClosed();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void publicHistoryIncludesFutureMonthsInDescendingOrderWithoutWrites() throws Exception {
        rows.add(row("9999-12", "11"));
        mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", "VAT_RATE"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].aply_bgn_ym").value("9999-12"))
            .andExpect(jsonPath("$.data[1].num_val").value(10));
        verify(mapper).selectHistory("VAT_RATE");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void publicSaveReturnsEmpty204AndReadbackRetainsOldMonthAndNullActorHistory() throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                .contentType("application/json").content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11}"))
            .andExpect(status().isNoContent()).andExpect(content().bytes(new byte[0]))
            .andExpect(header().exists("X-Request-Id"));
        mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", "VAT_RATE"))
            .andExpect(jsonPath("$.data[0].num_val").value(11))
            .andExpect(jsonPath("$.data[1].num_val").value(10));
        verify(mapper).insertHistory("VAT_RATE", "2026-11", "10", "11", null);
        assertThat(rows.get(1).getOprtStdNm()).isEqualTo(original.getOprtStdNm());
        assertThat(rows.get(1).getUnitNm()).isEqualTo(original.getUnitNm());
        assertThat(rows.get(1).getUsePlcDesc()).isEqualTo(original.getUsePlcDesc());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "101", "1.00001", "100000000000000", "null", "true", "\"11\""})
    void invalidNumericValuesNeverWrite(String value) throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                .contentType("application/json")
                .content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":" + value + "}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("num_val"))
            .andExpect(jsonPath("$.message").value("올바른 값을 입력하세요."));
        verify(mapper, never()).upsert(any(), any());
        verify(mapper, never()).insertHistory(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "100"})
    void numericBoundariesAreAllowed(String value) throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                .contentType("application/json")
                .content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":" + value + "}"))
            .andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{", "[]", "null", "{}", "{\"aply_bgn_ym\":\"2026-13\",\"num_val\":11}",
        "{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11,\"txt_val\":\"x\"}",
        "{\"aply_bgn_ym\":\"2026-11\",\"txt_val\":\"x\"}",
        "{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11,\"created_by\":1}"
    })
    void malformedUnknownOrWrongKindPayloadIs400WithoutWrites(String body) throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                .contentType("application/json").content(body))
            .andExpect(status().isBadRequest());
        verify(mapper, never()).upsert(any(), any());
        verify(mapper, never()).insertHistory(any(), any(), any(), any(), any());
    }

    @Test
    void closedMonthConflictDoesNotWrite() throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                .contentType("application/json").content("{\"aply_bgn_ym\":\"2026-09\",\"num_val\":11}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));
        verify(mapper, never()).upsert(any(), any());
        verify(mapper, never()).insertHistory(any(), any(), any(), any(), any());
    }

    @Test
    void unknownHistoryIs404() throws Exception {
        mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", "ABSENT"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.field").value("oprt_std_id"));
    }

    @Test
    void unknownSaveIs404() throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "ABSENT")
                .contentType("application/json").content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11}"))
            .andExpect(status().isNotFound());
        verify(mapper, never()).upsert(any(), any());
    }

    @Test
    void longHistoryKeyIsReadableButCannotBeWrittenToNarrowAuditKey() throws Exception {
        String key = "A".repeat(31);
        when(mapper.selectHistory(key)).thenReturn(List.of(original));
        mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", key))
            .andExpect(status().isOk());
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", key)
                .contentType("application/json").content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("oprt_std_id"));
        verify(mapper, never()).upsert(any(), any());
    }

    @Test
    void absentClosedMonthAllowsValidMonthAndSameMonthIsLastWriteWins() throws Exception {
        when(mapper.selectLastClosed()).thenReturn(null);
        for (int value : new int[]{11, 12}) {
            mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                    .contentType("application/json")
                    .content("{\"aply_bgn_ym\":\"1999-01\",\"num_val\":" + value + "}"))
                .andExpect(status().isNoContent());
        }
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().filter(r -> r.getAplyBgnYm().equals("1999-01")).findFirst().orElseThrow()
            .getNumVal()).isEqualByComparingTo("12");
        assertThat(original.getNumVal()).isEqualByComparingTo("10");
        verify(mapper).insertHistory("VAT_RATE", "1999-01", "11", "12", null);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 200, 201})
    void textLengthBoundsPreserveExistingKind(int length) throws Exception {
        original.setNumVal(null);
        original.setTxtVal("before");
        String body = new ObjectMapper().writeValueAsString(
            java.util.Map.of("aply_bgn_ym", "2026-11", "txt_val", "x".repeat(length)));
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                .contentType("application/json").content(body))
            .andExpect(status().is(length == 0 || length == 201 ? 400 : 204));
    }

    @Test
    void invalidOriginalKindIsRefused() throws Exception {
        original.setTxtVal("both");
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                .contentType("application/json").content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11}"))
            .andExpect(status().isBadRequest());
        verify(mapper, never()).upsert(any(), any());
    }

    @Test
    void unexpectedFailureDoesNotExposeSqlOrSecrets() throws Exception {
        when(mapper.selectHistory("VAT_RATE")).thenThrow(new IllegalStateException("SQL password private"));
        var response = mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", "VAT_RATE"))
            .andExpect(status().isInternalServerError()).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("SQL", "password", "private");
    }

    @Test
    void durableApprovedFixtureDeclaresAllOwnedOperations() throws Exception {
        String contract = new ClassPathResource("contracts/openapi.yaml").getContentAsString(
            java.nio.charset.StandardCharsets.UTF_8);
        assertThat(contract).contains("getOperatingStandards", "saveOperatingStandard", "getOperatingStandardHistory");
    }
}
