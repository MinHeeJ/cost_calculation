package com.sicost.operatingstandards;

import com.sicost.common.CurrentUser;
import com.sicost.common.GlobalExceptionHandler;
import com.sicost.common.PermissionService;
import com.sicost.common.RequestIdFilter;
import com.sicost.persistence.AuditContext;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실 controller/service와 stateful 매퍼 경계를 통한 공개 HTTP 계약. SQL 검증은 별도 DB 시험이다. */
class OperatingStandardContractTest {
    private OperatingStandardMapper mapper;
    private MockMvc mvc;
    private List<OperatingStandardRow> rows;

    @BeforeEach
    void setUp() {
        mapper = mock(OperatingStandardMapper.class);
        rows = new ArrayList<>();
        rows.add(row("VAT_RATE", "2000-01", "10", null, "%"));
        rows.add(row("TARGET_UTIL_RATE", "2000-01", "80", null, "%p"));
        rows.add(row("LABEL", "2000-01", null, "문자", null));
        when(mapper.selectLastClosedMonth()).thenReturn("2026-09");
        when(mapper.selectHistory(anyString())).thenAnswer(call -> rows.stream()
            .filter(row -> row.getOprtStdId().equals(call.getArgument(0)))
            .sorted(Comparator.comparing(OperatingStandardRow::getAplyBgnYm).reversed()).toList());
        when(mapper.selectCurrent(anyString())).thenAnswer(call -> rows.stream()
            .filter(row -> row.getAplyBgnYm().compareTo(call.getArgument(0)) <= 0).toList());
        when(mapper.save(any(), isNull())).thenAnswer(call -> {
            OperatingStandardRow saved = call.getArgument(0);
            rows.removeIf(row -> row.getOprtStdId().equals(saved.getOprtStdId())
                && row.getAplyBgnYm().equals(saved.getAplyBgnYm()));
            rows.add(saved);
            return 1;
        });
        var service = new OperatingStandardService(
            mapper, new AuditContext(new CurrentUser()), new PermissionService()
        );
        mvc = MockMvcBuilders.standaloneSetup(new OperatingStandardController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
    }

    @Test
    void publicCurrentValuesUseTypedEnvelopeAndServerSeoulMonthWithoutWrites() throws Exception {
        rows.add(row("FUTURE_ONLY", "9999-12", "11", null, "%"));
        mvc.perform(get("/api/admin/operating-standards"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.items[0].oprt_std_id").value("VAT_RATE"))
            .andExpect(jsonPath("$.data.items[0].num_val").value(10))
            .andExpect(jsonPath("$.data.items[0].unit_nm").value("%"))
            .andExpect(jsonPath("$.data.items[0].use_plc_desc").value("계산"))
            .andExpect(jsonPath("$.data.last_closed_ym").value("2026-09"))
            .andExpect(jsonPath("$.data.data").doesNotExist())
            .andExpect(header().exists("X-Request-Id"));
        verify(mapper).selectCurrent(YearMonth.now(ZoneId.of("Asia/Seoul")).toString());
        verify(mapper).selectLastClosedMonth();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void emptyCurrentCollectionIs200AndNoClosedMonthIsNull() throws Exception {
        rows.clear();
        when(mapper.selectLastClosedMonth()).thenReturn(null);
        mvc.perform(get("/api/admin/operating-standards"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.last_closed_ym").isEmpty());
    }

    @Test
    void publicHistoryContainsFutureAndPriorMonthsDescendingWithoutWrites() throws Exception {
        rows.add(row("VAT_RATE", "9999-12", "11", null, "%"));
        mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", "VAT_RATE"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].aply_bgn_ym").value("9999-12"))
            .andExpect(jsonPath("$.data[0].num_val").value(11))
            .andExpect(jsonPath("$.data[1].num_val").value(10));
        verify(mapper).selectHistory("VAT_RATE");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void unknownHistoryKeyReturns404() throws Exception {
        mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", "MISSING"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.field").value("oprt_std_id"));
    }

    @Test
    void publicSaveReturnsEmpty204ThenReadbackPreservesPreviousMonthAndRecordsNullActor() throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11}"))
            .andExpect(status().isNoContent()).andExpect(content().string(""))
            .andExpect(header().exists("X-Request-Id"));
        mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", "VAT_RATE"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].num_val").value(11))
            .andExpect(jsonPath("$.data[1].num_val").value(10));
        var saved = ArgumentCaptor.forClass(OperatingStandardRow.class);
        verify(mapper).save(saved.capture(), isNull());
        assertThat(saved.getValue().getOprtStdNm()).isEqualTo("VAT_RATE 이름");
        assertThat(saved.getValue().getUsePlcDesc()).isEqualTo("계산");
        verify(mapper).appendHistory("VAT_RATE", "2026-11", "10", "11", null);
    }

    @Test
    void repeatedMonthChangesOnlyThatMonthAndNoOpDoesNotAudit() throws Exception {
        rows.add(row("VAT_RATE", "2026-11", "11", null, "%"));
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":12}"))
            .andExpect(status().isNoContent());
        assertThat(rows.stream().filter(row -> row.getOprtStdId().equals("VAT_RATE")).toList()).hasSize(2);
        verify(mapper).appendHistory("VAT_RATE", "2026-11", "11", "12", null);
        clearInvocations(mapper);
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":12.0000}"))
            .andExpect(status().isNoContent());
        verify(mapper, never()).save(any(), any());
        verify(mapper, never()).appendHistory(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "101", "0.00001", "100000000000000"})
    void invalidNumericValueIs400AndCannotWrite(String value) throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "TARGET_UTIL_RATE")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":" + value + "}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("num_val"))
            .andExpect(jsonPath("$.message").value("올바른 값을 입력하세요."));
        verify(mapper, never()).save(any(), any());
        verify(mapper, never()).appendHistory(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "100", "10.12340000"})
    void percentageBoundariesAndEffectiveScaleAreAccepted(String value) throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "TARGET_UTIL_RATE")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":" + value + "}"))
            .andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-00", "2026-1", "26-11"})
    void invalidMonthIs400WithoutMutation(String month) throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"aply_bgn_ym\":\"" + month + "\",\"num_val\":11}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("aply_bgn_ym"));
        verifyNoInteractions(mapper);
    }

    @Test
    void closedMonthIs409AndLeavesRowsAndHistoryUntouched() throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"2026-09\",\"num_val\":11}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));
        assertThat(rows).hasSize(3);
        verify(mapper, never()).save(any(), any());
        verify(mapper, never()).appendHistory(any(), any(), any(), any(), any());
    }

    @Test
    void noClosedMonthAllowsEarlierValidMonthAndUsesEarliestMetadata() throws Exception {
        when(mapper.selectLastClosedMonth()).thenReturn(null);
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"1999-01\",\"num_val\":11}"))
            .andExpect(status().isNoContent());
        verify(mapper).appendHistory("VAT_RATE", "1999-01", "10", "11", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"num_val\":11,\"txt_val\":\"x\"", "\"txt_val\":\"x\"",
        "\"num_val\":null", "\"num_val\":11,\"txt_val\":null", "\"unknown\":1"})
    void bothValuesTypeSwitchNullAndUnknownPropertiesAreRejected(String fields) throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"2026-11\"," + fields + "}"))
            .andExpect(status().isBadRequest());
        verify(mapper, never()).save(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 201})
    void textLengthOutsideContractIsRejected(int length) throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "LABEL")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"aply_bgn_ym\":\"2026-11\",\"txt_val\":\"" + "a".repeat(length) + "\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("txt_val"));
        verify(mapper, never()).save(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 200})
    void textBoundariesAreSaved(int length) throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "LABEL")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"aply_bgn_ym\":\"2026-11\",\"txt_val\":\"" + "a".repeat(length) + "\"}"))
            .andExpect(status().isNoContent());
        verify(mapper).appendHistory("LABEL", "2026-11", "문자", "a".repeat(length), null);
    }

    @Test
    void unknownSaveKeyIs404WithoutMutation() throws Exception {
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "MISSING")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11}"))
            .andExpect(status().isNotFound());
        verify(mapper, never()).save(any(), any());
    }

    @Test
    void longExistingKeyCanBeReadButCannotBeTruncatedForHistoryWrite() throws Exception {
        String key = "A".repeat(50);
        rows.add(row(key, "2000-01", "10", null, "%"));
        mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", key))
            .andExpect(status().isOk());
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", key)
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("oprt_std_id"));
        verify(mapper, never()).save(any(), any());
    }

    @Test
    void ambiguousExistingValueDoesNotInferType() throws Exception {
        rows.get(0).setTxtVal("ambiguous");
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11}"))
            .andExpect(status().isBadRequest());
        verify(mapper, never()).save(any(), any());
    }

    @Test
    void serverFailuresNeverExposeSqlAndClasspathContractIsAvailable() throws Exception {
        try (var fixture = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            assertThat(new String(fixture.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("operationId: saveOperatingStandard", "operationId: getOperatingStandardHistory");
        }
        when(mapper.selectHistory("VAT_RATE")).thenThrow(new IllegalStateException("SQL password=private"));
        var result = mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", "VAT_RATE"))
            .andExpect(status().isInternalServerError()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQL", "private", "password");
    }

    @Test
    void metadataUsesLatestRowBeforeRequestedMonthNotFutureMetadata() throws Exception {
        var future = row("VAT_RATE", "2027-01", "12", null, "다른 단위");
        future.setOprtStdNm("미래 이름");
        rows.add(future);
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11}"))
            .andExpect(status().isNoContent());
        var saved = ArgumentCaptor.forClass(OperatingStandardRow.class);
        verify(mapper).save(saved.capture(), isNull());
        assertThat(saved.getValue().getOprtStdNm()).isEqualTo("VAT_RATE 이름");
        assertThat(saved.getValue().getUnitNm()).isEqualTo("%");
    }

    @Test
    void nonPercentageDecimalAcceptsFourteenIntegerAndFourFractionDigitsWithoutRounding() throws Exception {
        rows.get(0).setUnitNm("원");
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":99999999999999.9999}"))
            .andExpect(status().isNoContent());
        var saved = ArgumentCaptor.forClass(OperatingStandardRow.class);
        verify(mapper).save(saved.capture(), isNull());
        assertThat(saved.getValue().getNumVal()).isEqualByComparingTo("99999999999999.9999");
    }

    private OperatingStandardRow row(String key, String month, String number, String text, String unit) {
        var row = new OperatingStandardRow();
        row.setOprtStdId(key);
        row.setAplyBgnYm(month);
        row.setOprtStdNm(key + " 이름");
        row.setNumVal(number == null ? null : new BigDecimal(number));
        row.setTxtVal(text);
        row.setUnitNm(unit);
        row.setUsePlcDesc("계산");
        return row;
    }
}
