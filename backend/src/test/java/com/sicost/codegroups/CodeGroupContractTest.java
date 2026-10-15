package com.sicost.codegroups;

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
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 controller/service와 persistence 경계만 대역으로 사용하는 HTTP 계약 시험이다. */
class CodeGroupContractTest {
    private CodeGroupMapper mapper;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private CodeGroupRow group;
    private AdminCodeRow code;

    @BeforeEach
    void setUp() {
        mapper = mock(CodeGroupMapper.class);
        var service = new CodeGroupService(mapper, new AuditContext(new CurrentUser()), new PermissionService());
        mvc = MockMvcBuilders.standaloneSetup(new CodeGroupController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
        group = new CodeGroupRow();
        group.setCdGrpId("JOB");
        group.setCdGrpNm("직무");
        group.setCdGrpSeCd("BIZ");
        group.setAttr1Nm("기본 단가(원)");
        group.setSortSeq(1);
        group.setUseYn("Y");
        group.setCodeCount(12L);
        code = new AdminCodeRow();
        code.setCdGrpId("JOB");
        code.setDtlCd("BE");
        code.setCdNm("백엔드");
        code.setSortSeq(1);
        code.setUseYn("Y");
        code.setAttr1Val("200000");
        when(mapper.selectGroup("JOB")).thenReturn(group);
        when(mapper.selectCodes("JOB")).thenReturn(List.of(code));
    }

    @Test
    void publicGroupListHasTypedSingleEnvelopeAndNoWrites() throws Exception {
        when(mapper.selectGroups()).thenReturn(List.of(group));
        mvc.perform(get("/api/admin/code-groups"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code_count").value(12))
            .andExpect(jsonPath("$.data[0].attr1_nm").value("기본 단가(원)"))
            .andExpect(jsonPath("$.data.data").doesNotExist());
        verify(mapper).selectGroups();
        verifyNoMoreInteractions(mapper);
        assertThat(new ClassPathResource("contracts/openapi.yaml").getInputStream().readAllBytes()).isNotEmpty();
    }

    @Test
    void publicCodesIncludeDisabledAndDoNotWrite() throws Exception {
        code.setUseYn("N");
        mvc.perform(get("/api/admin/code-groups/{group}/codes", "JOB"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].use_yn").value("N"));
        verify(mapper).selectGroup("JOB");
        verify(mapper).selectCodes("JOB");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void emptyExistingGroupReturnsArray() throws Exception {
        when(mapper.selectCodes("JOB")).thenReturn(List.of());
        mvc.perform(get("/api/admin/code-groups/{group}/codes", "JOB"))
            .andExpect(status().isOk()).andExpect(content().json("{\"data\":[]}"));
    }

    @Test
    void unknownGroupIs404() throws Exception {
        mvc.perform(get("/api/admin/code-groups/{group}/codes", "UNKNOWN"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.field").value("group"));
    }

    @Test
    void publicCreateIsBodylessAndAuditsNullCodeAndActor() throws Exception {
        mvc.perform(post("/api/admin/code-groups").contentType("application/json")
            .content("{\"cd_grp_id\":\"TEST_GROUP\",\"cd_grp_nm\":\"테스트 그룹\"}"))
            .andExpect(status().isNoContent()).andExpect(content().string(""))
            .andExpect(header().exists("X-Request-Id"));
        verify(mapper).insertGroup(argThat(row -> row.getCdGrpId().equals("TEST_GROUP")), isNull());
        verify(mapper).insertHistory("TEST_GROUP", null, "ADD", null, null, "테스트 그룹", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"cd_grp_id\":\"TEST1\",\"cd_grp_nm\":\"테스트\"}",
        "{\"cd_grp_id\":\"TEST_GROUP\",\"cd_grp_nm\":\"\"}",
        "{\"cd_grp_id\":\"TEST_GROUP\",\"cd_grp_nm\":\"테스트\",\"created_by\":1}"
    })
    void invalidGroupHasNoWrites(String input) throws Exception {
        mvc.perform(post("/api/admin/code-groups").contentType("application/json").content(input))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").isString());
        verifyNoInteractions(mapper);
    }

    @Test
    void duplicateGroupIs409WithoutMutation() throws Exception {
        mvc.perform(post("/api/admin/code-groups").contentType("application/json")
            .content("{\"cd_grp_id\":\"JOB\",\"cd_grp_nm\":\"중복\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        verify(mapper, never()).insertGroup(any(), any());
    }

    @Test
    void fullSaveAddsNewCodeAsYAndReadbackUsesActualWrite() throws Exception {
        doAnswer(invocation -> {
            AdminCodeRow added = invocation.getArgument(0);
            when(mapper.selectCodes("JOB")).thenReturn(List.of(code, added));
            return 1;
        }).when(mapper).insertCode(any(), isNull());
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "JOB").contentType("application/json")
            .content("{\"codes\":[" + row("BE", "백엔드", "Y", "") + ","
                + row("TEST1", "테스트", "N", "") + "]}"))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/admin/code-groups/{group}/codes", "JOB"))
            .andExpect(jsonPath("$.data[1].dtl_cd").value("TEST1"))
            .andExpect(jsonPath("$.data[1].use_yn").value("Y"));
        verify(mapper).insertHistory("JOB", "TEST1", "ADD", null, null, "테스트", null);
    }

    @Test
    void attributeChangeWritesOldAndNewValuesAndNullActor() throws Exception {
        save(row("BE", "백엔드", "Y", ",\"attr1_val\":\"250000\""), 204);
        verify(mapper).updateCode(any(), eq(Map.of("attr1_val", "250000")), isNull());
        verify(mapper).insertHistory("JOB", "BE", "UPDATE", "attr1_val", "200000", "250000", null);
    }

    @Test
    void omittedOptionalValueRemainsAndNoopHasNoAuditUpdate() throws Exception {
        save(row("BE", "백엔드", "Y", ""), 204);
        verify(mapper, never()).updateCode(any(), any(), any());
        verify(mapper, never()).insertHistory(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void explicitNullClearsOptionalAttribute() throws Exception {
        save(row("BE", "백엔드", "Y", ",\"attr1_val\":null"), 204);
        verify(mapper).updateCode(argThat(value -> value.getAttr1Val() == null), any(), isNull());
        verify(mapper).insertHistory("JOB", "BE", "UPDATE", "attr1_val", "200000", null, null);
    }

    @Test
    void missingExistingRowRejectsAllWrites() throws Exception {
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "JOB")
            .contentType("application/json").content("{\"codes\":[]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("codes"));
        noWrites();
    }

    @Test
    void duplicateCodeRejectsAllWrites() throws Exception {
        save(row("BE", "변경", "Y", "") + "," + row("BE", "중복", "Y", ""), 409);
        noWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "lower", "A-B"})
    void invalidCodeIs400(String id) throws Exception {
        save(row(id, "이름", "Y", ""), 400);
        noWrites();
    }

    @Test
    void emptyNameUsesContractMessage() throws Exception {
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "JOB").contentType("application/json")
            .content("{\"codes\":[" + row("BE", "", "Y", "") + "]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("이름을 입력하세요."));
        noWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {",\"attr1_val\":null", ",\"aply_bgn_dt\":\"2026-10-01\""})
    void systemMixedForbiddenChangesHaveNoWrites(String extra) throws Exception {
        group.setCdGrpSeCd("SYSTEM");
        save(row("BE", "허용 이름 변경", "Y", extra), 409);
        noWrites();
    }

    @Test
    void systemNewCodeRejected() throws Exception {
        group.setCdGrpSeCd("SYSTEM");
        save(row("BE", "변경", "Y", "") + "," + row("NEW", "신규", "Y", ""), 409);
        noWrites();
    }

    @Test
    void systemUseChangeRejectedWithSpecificMessage() throws Exception {
        group.setCdGrpSeCd("SYSTEM");
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "JOB").contentType("application/json")
            .content("{\"codes\":[" + row("BE", "변경", "N", "") + "]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("시스템 코드는 사용 중지할 수 없습니다."));
        noWrites();
    }

    @Test
    void systemNameChangeIsAllowed() throws Exception {
        group.setCdGrpSeCd("SYSTEM");
        save(row("BE", "턴키(일괄)", "Y", ""), 204);
        verify(mapper).updateCode(any(), eq(Map.of("cd_nm", "턴키(일괄)")), isNull());
    }

    @Test
    void disableAndEnableProduceDistinctHistoryTypes() throws Exception {
        save(row("BE", "백엔드", "N", ""), 204);
        verify(mapper).insertHistory("JOB", "BE", "DISABLE", "use_yn", "Y", "N", null);
        code.setUseYn("N");
        save(row("BE", "백엔드", "Y", ""), 204);
        verify(mapper).insertHistory("JOB", "BE", "ENABLE", "use_yn", "N", "Y", null);
    }

    @Test
    void unknownFieldRejectedBeforeAnyWrite() throws Exception {
        save(row("BE", "변경", "Y", ",\"updated_by\":7"), 400);
        noWrites();
    }

    @Test
    void persistenceErrorIsSanitized() throws Exception {
        when(mapper.selectGroups()).thenThrow(new IllegalStateException("SQL jdbc password private"));
        var response = mvc.perform(get("/api/admin/code-groups"))
            .andExpect(status().isInternalServerError()).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("SQL", "jdbc", "password", "private");
    }

    private void noWrites() {
        verify(mapper, never()).updateCode(any(), any(), any());
        verify(mapper, never()).insertCode(any(), any());
        verify(mapper, never()).insertHistory(any(), any(), any(), any(), any(), any(), any());
    }

    private void save(String rows, int status) throws Exception {
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "JOB").contentType("application/json")
            .content("{\"codes\":[" + rows + "]}"))
            .andExpect(status().is(status));
    }

    private String row(String id, String name, String use, String extra) throws Exception {
        return "{\"dtl_cd\":" + json.writeValueAsString(id) + ",\"cd_nm\":" + json.writeValueAsString(name)
            + ",\"sort_seq\":1,\"use_yn\":\"" + use + "\"" + extra + "}";
    }
}
