package com.sicost.excelmaps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.CurrentUser;
import com.sicost.common.GlobalExceptionHandler;
import com.sicost.common.PermissionService;
import com.sicost.common.RequestIdFilter;
import com.sicost.persistence.AuditContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP/controller/service with only the persistence boundary doubled, like MonthCloseContractTest. */
class ExcelMapContractTest {
    private ExcelMapMapper mapper;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private ExcelMapRow stored;

    @BeforeEach
    void setUp() {
        mapper = mock(ExcelMapMapper.class);
        var service = new ExcelMapService(mapper, new AuditContext(new CurrentUser()), new PermissionService());
        mvc = MockMvcBuilders.standaloneSetup(new ExcelMapController(service))
            .setControllerAdvice(new ExcelMapExceptionHandler(), new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
        stored = row();
        when(mapper.selectCodeUse("JOB", "BE")).thenReturn("Y");
        when(mapper.selectById(7L)).thenAnswer(call -> stored);
        when(mapper.update(any())).thenReturn(1);
        when(mapper.insert(any(), isNull())).thenAnswer(call -> {
            stored = call.getArgument(0);
            stored.setMapId(8L);
            stored.setCdGrpNm("직무");
            stored.setCdNm("백엔드");
            return 1;
        });
        when(mapper.selectAll()).thenAnswer(call -> List.of(stored));
    }

    private ExcelMapRow row() {
        var row = new ExcelMapRow();
        row.setMapId(7L);
        row.setExcelLblNm("테스트표기");
        row.setTrnsCdGrpId("JOB");
        row.setTrnsCd("BE");
        row.setMapRoleCd("MAIN");
        row.setRmrkDesc("원본");
        row.setUseYn("Y");
        row.setCdGrpNm("직무");
        row.setCdNm("백엔드");
        return row;
    }

    private String createBody() throws Exception {
        return json.writeValueAsString(Map.of("excel_lbl_nm", "테스트표기", "trns_cd_grp_id", "JOB",
            "trns_cd", "BE", "map_role_cd", "MAIN"));
    }

    @Test
    void publicListIncludesJoinedNamesAndInactiveMappingsWithoutWrites() throws Exception {
        stored.setUseYn("N");
        mvc.perform(get("/api/admin/excel-maps"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].map_id").value(7))
            .andExpect(jsonPath("$.data[0].cd_grp_nm").value("직무"))
            .andExpect(jsonPath("$.data[0].cd_nm").value("백엔드"))
            .andExpect(jsonPath("$.data[0].use_yn").value("N"))
            .andExpect(jsonPath("$.data[0].map_role_cd").value("MAIN"))
            .andExpect(jsonPath("$.data.data").doesNotExist());
        verify(mapper).selectAll();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void publicCreateReturnsEmpty204AndReadbackUsesActualInsertWithNullActor() throws Exception {
        var result = mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(createBody()))
            .andExpect(status().isNoContent()).andExpect(content().bytes(new byte[0]))
            .andExpect(header().exists("X-Request-Id")).andReturn();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotBlank();
        mvc.perform(get("/api/admin/excel-maps"))
            .andExpect(jsonPath("$.data[0].map_id").value(8))
            .andExpect(jsonPath("$.data[0].use_yn").value("Y"));
        verify(mapper).insertHistory(same(stored), eq("ADD"), eq("excel_map.8"), isNull(),
            eq("테스트표기"), isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"excel_lbl_nm", "trns_cd_grp_id", "trns_cd"})
    void emptyRequiredInputHasExactFieldAndNoWrites(String field) throws Exception {
        var body = json.readTree(createBody());
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).put(field, "");
        mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field))
            .andExpect(jsonPath("$.message").value(field.equals("excel_lbl_nm")
                ? "엑셀 글자를 입력하세요." : "대응할 코드를 고르세요."));
        verifyNoInteractions(mapper);
    }

    @Test
    void duplicateReturnsBusinessCodeAndNeverWrites() throws Exception {
        when(mapper.countDuplicate(any())).thenReturn(1);
        var result = mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(createBody()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"))
            .andExpect(jsonPath("$.message").value("이미 있는 대응입니다.")).andReturn();
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("requestId").asText()).isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        assertThat(body.path("meta").path("requestId").asText()).isEqualTo(body.path("requestId").asText());
        verify(mapper, never()).insert(any(), any());
        verify(mapper, never()).insertHistory(any(), any(), any(), any(), any(), any());
    }

    @Test
    void uniqueConstraintRaceAlsoReturnsDuplicateCode() throws Exception {
        doThrow(new DuplicateKeyException("private SQL")).when(mapper).insert(any(), isNull());
        mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(createBody()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        verify(mapper, never()).insertHistory(any(), any(), any(), any(), any(), any());
    }

    @Test
    void inactiveTargetIsRejectedWithoutInsert() throws Exception {
        when(mapper.selectCodeUse("JOB", "BE")).thenReturn("N");
        mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(createBody()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("trns_cd"));
        verify(mapper, never()).insert(any(), any());
    }

    @Test
    void missingTargetReturns404WithoutInsert() throws Exception {
        when(mapper.selectCodeUse("JOB", "BE")).thenReturn(null);
        mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(createBody()))
            .andExpect(status().isNotFound());
        verify(mapper, never()).insert(any(), any());
    }

    @Test
    void publicPatchPreservesIdentityAndWritesEachChangedField() throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 7)
            .contentType("application/json").content("{\"map_role_cd\":\"SUB\",\"rmrk_desc\":\"수정\"}"))
            .andExpect(status().isNoContent()).andExpect(content().bytes(new byte[0]))
            .andExpect(header().exists("X-Request-Id"));
        mvc.perform(get("/api/admin/excel-maps"))
            .andExpect(jsonPath("$.data[0].map_id").value(7))
            .andExpect(jsonPath("$.data[0].excel_lbl_nm").value("테스트표기"))
            .andExpect(jsonPath("$.data[0].trns_cd_grp_id").value("JOB"))
            .andExpect(jsonPath("$.data[0].trns_cd").value("BE"))
            .andExpect(jsonPath("$.data[0].map_role_cd").value("SUB"))
            .andExpect(jsonPath("$.data[0].rmrk_desc").value("수정"));
        verify(mapper).insertHistory(stored, "UPDATE", "excel_map.7.map_role_cd", "MAIN", "SUB", null);
        verify(mapper).insertHistory(stored, "UPDATE", "excel_map.7.rmrk_desc", "원본", "수정", null);
    }

    @Test
    void omittedMemoIsPreservedButExplicitNullClearsIt() throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 7).contentType("application/json")
            .content("{\"map_role_cd\":\"SUB\"}")).andExpect(status().isNoContent());
        assertThat(stored.getRmrkDesc()).isEqualTo("원본");
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 7).contentType("application/json")
            .content("{\"rmrk_desc\":null}")).andExpect(status().isNoContent());
        assertThat(stored.getRmrkDesc()).isNull();
        verify(mapper).insertHistory(stored, "UPDATE", "excel_map.7.rmrk_desc", "원본", null, null);
    }

    @Test
    void disableEnableAndNoOpKeepRowAndCorrectHistory() throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 7).contentType("application/json")
            .content("{\"use_yn\":\"N\"}")).andExpect(status().isNoContent());
        verify(mapper).insertHistory(stored, "DISABLE", "excel_map.7.use_yn", "Y", "N", null);
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 7).contentType("application/json")
            .content("{\"use_yn\":\"Y\"}")).andExpect(status().isNoContent());
        verify(mapper).insertHistory(stored, "ENABLE", "excel_map.7.use_yn", "N", "Y", null);
        clearInvocations(mapper);
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 7).contentType("application/json")
            .content("{\"use_yn\":\"Y\"}")).andExpect(status().isNoContent());
        verify(mapper).selectById(7);
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"map_id", "excel_lbl_nm", "trns_cd_grp_id", "trns_cd", "created_by", "other"})
    void immutableAndUnknownFieldsAreRejected(String field) throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 7).contentType("application/json")
            .content(json.writeValueAsString(Map.of(field, "changed"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "null", "{", "{\"map_role_cd\":\"OTHER\"}",
        "{\"map_role_cd\":null}", "{\"use_yn\":\"OTHER\"}", "{\"rmrk_desc\":1}"})
    void malformedOrInvalidPatchNeverTouchesPersistence(String body) throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 7).contentType("application/json").content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(mapper);
    }

    @Test
    void longMemoIsRejectedOnBothWrites() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(createBody());
        body.put("rmrk_desc", "x".repeat(201));
        mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("rmrk_desc"));
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 7).contentType("application/json")
            .content(json.writeValueAsString(Map.of("rmrk_desc", "x".repeat(201)))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("rmrk_desc"));
        verifyNoInteractions(mapper);
    }

    @Test
    void nonexistentMapReturns404() throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 999).contentType("application/json")
            .content("{\"rmrk_desc\":\"수정\"}")).andExpect(status().isNotFound());
        verify(mapper, never()).update(any());
    }

    @Test
    void historyFailureIs500WithoutSensitiveLeakageOnEachWrite() throws Exception {
        doThrow(new IllegalStateException("SQL jdbc password=private"))
            .when(mapper).insertHistory(any(), any(), any(), any(), any(), any());
        var created = mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(createBody()))
            .andExpect(status().isInternalServerError()).andReturn();
        assertThat(created.getResponse().getContentAsString()).doesNotContain("SQL", "jdbc", "private");
        var changed = mvc.perform(patch("/api/admin/excel-maps/{map_id}", 7).contentType("application/json")
            .content("{\"rmrk_desc\":\"수정\"}"))
            .andExpect(status().isInternalServerError()).andReturn();
        assertThat(changed.getResponse().getContentAsString()).doesNotContain("SQL", "jdbc", "private");
    }
}
