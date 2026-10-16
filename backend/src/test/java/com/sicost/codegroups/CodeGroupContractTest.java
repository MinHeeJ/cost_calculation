package com.sicost.codegroups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.CurrentUser;
import com.sicost.common.GlobalExceptionHandler;
import com.sicost.common.PermissionService;
import com.sicost.common.RequestIdFilter;
import com.sicost.persistence.AuditContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller/service with a persistence boundary double; SQL durability has a separate opt-in test. */
class CodeGroupContractTest {
    private CodeGroupMapper mapper;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private CodeGroupRow group;
    private CodeDetailRow row;

    @BeforeEach
    void setUp() {
        mapper = mock(CodeGroupMapper.class);
        var service = new CodeGroupService(mapper, new AuditContext(new CurrentUser()), new PermissionService());
        mvc = MockMvcBuilders.standaloneSetup(new CodeGroupController(service))
            .setControllerAdvice(new CodeGroupAdvice(), new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
        group = new CodeGroupRow();
        group.setCdGrpId("POSITION");
        group.setCdGrpNm("직급");
        group.setCdGrpSeCd("BIZ");
        group.setUseYn("Y");
        row = new CodeDetailRow();
        row.setCdGrpId("POSITION");
        row.setDtlCd("TL");
        row.setCdNm("팀장");
        row.setAttr1Val("200000");
        row.setUseYn("Y");
        row.setSortSeq(1);
        when(mapper.selectGroup("POSITION")).thenReturn(group);
        when(mapper.selectCodes("POSITION")).thenReturn(List.of(row));
    }

    private String payload(String fields) {
        return "{\"codes\":[{\"dtl_cd\":\"TL\",\"cd_nm\":\"팀장\",\"sort_seq\":1,\"use_yn\":\"Y\""
            + fields + "}]}";
    }

    @Test
    void publicGroupReadHasSingleTypedEnvelopeAndDoesNotWrite() throws Exception {
        when(mapper.selectGroups()).thenReturn(List.of(group));
        mvc.perform(get("/api/admin/code-groups"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].cd_grp_id").value("POSITION"))
            .andExpect(jsonPath("$.data[0].attr1_nm").isEmpty())
            .andExpect(jsonPath("$.data.data").doesNotExist());
        verify(mapper).selectGroups();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void publicAdminReadIncludesInactiveAndNullsWithoutWriting() throws Exception {
        row.setUseYn("N");
        mvc.perform(get("/api/admin/code-groups/{group}/codes", "POSITION"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].use_yn").value("N"))
            .andExpect(jsonPath("$.data[0].attr1_val").value("200000"))
            .andExpect(jsonPath("$.data[0].aply_bgn_dt").isEmpty());
        verify(mapper).selectGroup("POSITION");
        verify(mapper).selectCodes("POSITION");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void absentGroupReturns404ButExistingEmptyGroupIs200() throws Exception {
        mvc.perform(get("/api/admin/code-groups/{group}/codes", "ABSENT"))
            .andExpect(status().isNotFound());
        when(mapper.selectCodes("POSITION")).thenReturn(List.of());
        mvc.perform(get("/api/admin/code-groups/{group}/codes", "POSITION"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void publicCreateIs204WithRequestIdAndSingleNullActorGroupAudit() throws Exception {
        mvc.perform(post("/api/admin/code-groups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cd_grp_id\":\"TEST_GROUP\",\"cd_grp_nm\":\"테스트 그룹\"}"))
            .andExpect(status().isNoContent()).andExpect(content().string(""))
            .andExpect(header().exists("X-Request-Id"));
        verify(mapper).insertGroup(argThat(v -> v.get("actor") == null && v.get("cd_grp_id").equals("TEST_GROUP")));
        verify(mapper).insertHistory(argThat(v -> v.get("code") == null && v.get("actor") == null
            && v.get("type").equals("ADD") && v.get("group").equals("TEST_GROUP")));
        verify(mapper, never()).insertCode(anyMap());
    }

    @ParameterizedTest
    @CsvSource({"TEST1,이름,cd_grp_id", "TEST_GROUP,'',cd_grp_nm"})
    void invalidGroupCannotWrite(String id, String name, String field) throws Exception {
        mvc.perform(post("/api/admin/code-groups").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("cd_grp_id", id, "cd_grp_nm", name))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        verify(mapper, never()).insertGroup(anyMap());
        verify(mapper, never()).insertHistory(anyMap());
    }

    @Test
    void duplicateGroupIsConflictWithoutWrites() throws Exception {
        mvc.perform(post("/api/admin/code-groups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cd_grp_id\":\"POSITION\",\"cd_grp_nm\":\"직급\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        verify(mapper, never()).insertGroup(anyMap());
    }

    @Test
    void updateRecordsExactBeforeAfterAndNullActorThenReadbackUsesRetainedWrite() throws Exception {
        doAnswer(call -> {
            Map<String, Object> write = call.getArgument(0);
            Map<String, Object> changes = (Map<String, Object>) write.get("changes");
            row.setAttr1Val((String) changes.get("attr1_val"));
            return 1;
        }).when(mapper).updateCode(anyMap());
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(payload(",\"attr1_val\":\"250000\"")))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/admin/code-groups/{group}/codes", "POSITION"))
            .andExpect(jsonPath("$.data[0].attr1_val").value("250000"));
        verify(mapper).insertHistory(argThat(v -> "attr1_val".equals(v.get("item"))
            && "200000".equals(v.get("before")) && "250000".equals(v.get("after")) && v.get("actor") == null));
    }

    @Test
    void omittedNullableFieldsAreNoOpButExplicitNullClearsAndAudits() throws Exception {
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(payload("")))
            .andExpect(status().isNoContent());
        verify(mapper, never()).updateCode(anyMap());
        verify(mapper, never()).insertHistory(anyMap());
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(payload(",\"attr1_val\":null")))
            .andExpect(status().isNoContent());
        verify(mapper).updateCode(argThat(v -> ((Map<?, ?>) v.get("changes")).containsKey("attr1_val")
            && ((Map<?, ?>) v.get("changes")).get("attr1_val") == null));
    }

    @Test
    void newCodeUsesNaturalKeyAndForcesYWithOneAddHistory() throws Exception {
        var body = json.readTree(payload(""));
        var codes = (com.fasterxml.jackson.databind.node.ArrayNode) body.get("codes");
        codes.addObject().put("dtl_cd", "TEST1").put("cd_nm", "테스트").put("sort_seq", 2).put("use_yn", "N");
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isNoContent());
        verify(mapper).insertCode(argThat(v -> "TEST1".equals(v.get("dtl_cd")) && "Y".equals(v.get("use_yn"))));
        verify(mapper).insertHistory(argThat(v -> "ADD".equals(v.get("type")) && "TEST1".equals(v.get("code"))));
    }

    @Test
    void missingExistingCodeIs400AndNoWrites() throws Exception {
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content("{\"codes\":[]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("codes"));
        noWrites();
    }

    @Test
    void duplicateCodeIs409AndNoWrites() throws Exception {
        var body = json.readTree(payload(""));
        ((com.fasterxml.jackson.databind.node.ArrayNode) body.get("codes")).add(body.get("codes").get(0).deepCopy());
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"))
            .andExpect(jsonPath("$.message").value("이미 있는 코드입니다."));
        noWrites();
    }

    @ParameterizedTest
    @CsvSource({"dtl_cd,코드를 입력하세요.", "cd_nm,이름을 입력하세요."})
    void requiredMessagesAreStable(String field, String message) throws Exception {
        var body = json.readTree(payload(""));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("codes").get(0)).put(field, "");
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(message));
        noWrites();
    }

    @ParameterizedTest
    @CsvSource({"attr1_val,x", "aply_bgn_dt,2026-10-16", "use_yn,N"})
    void systemForbiddenFieldMixedWithNameRollsBackBeforeWriting(String field, String value) throws Exception {
        group.setCdGrpSeCd("SYSTEM");
        var body = json.readTree(payload(""));
        var input = (com.fasterxml.jackson.databind.node.ObjectNode) body.get("codes").get(0);
        input.put("cd_nm", "새 이름");
        input.put(field, value);
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SYSTEM_CODE_LOCKED"))
            .andExpect(jsonPath("$.message").value(field.equals("use_yn")
                ? "시스템 코드는 사용 중지할 수 없습니다." : "시스템 코드는 이름과 순서만 바꿀 수 있습니다."));
        noWrites();
    }

    @Test
    void systemNameAndSortAreAllowedAndOmittedAttributesPreserved() throws Exception {
        group.setCdGrpSeCd("SYSTEM");
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(payload("").replace("팀장", "턴키(일괄)")))
            .andExpect(status().isNoContent());
        verify(mapper).updateCode(argThat(v -> ((Map<?, ?>) v.get("changes")).size() == 1));
    }

    @Test
    void systemNewCodeIsForbidden() throws Exception {
        group.setCdGrpSeCd("SYSTEM");
        var body = json.readTree(payload(""));
        ((com.fasterxml.jackson.databind.node.ArrayNode) body.get("codes"))
            .addObject().put("dtl_cd", "NEW").put("cd_nm", "신규").put("sort_seq", 2).put("use_yn", "Y");
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isConflict());
        noWrites();
    }

    @ParameterizedTest
    @CsvSource({"Y,N,DISABLE", "N,Y,ENABLE"})
    void useTransitionsRecordTheirOwnHistoryType(String old, String next, String type) throws Exception {
        row.setUseYn(old);
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(payload("").replace("\"Y\"", "\"" + next + "\"")))
            .andExpect(status().isNoContent());
        verify(mapper).insertHistory(argThat(v -> type.equals(v.get("type")) && "use_yn".equals(v.get("item"))));
    }

    @Test
    void publicHistoryUsesDedicatedPageWithoutSortAndFilterMatchesCount() throws Exception {
        when(mapper.selectHistory("PARAM", 50, 0)).thenReturn(List.of());
        when(mapper.countHistory("PARAM")).thenReturn(51L);
        mvc.perform(get("/api/admin/code-history").param("cd_grp_id", "PARAM"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(51))
            .andExpect(jsonPath("$.data.size").value(50)).andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.sort").doesNotExist());
        verify(mapper).selectHistory("PARAM", 50, 0);
        verify(mapper).countHistory("PARAM");
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({"page,0", "size,30", "sort,'chg_at,asc'"})
    void historyInvalidPagingIs400BeforeQuery(String field, String value) throws Exception {
        mvc.perform(get("/api/admin/code-history").param(field, value))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({"'{',body", "'{\"codes\":[],\"extra\":1}',extra", "'{\"codes\":null}',codes"})
    void malformedAndUnknownPayloadsAre400(String body, String ignored) throws Exception {
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        noWrites();
    }

    @Test
    void historyFailureReturnsSafe500WithMatchingTrace() throws Exception {
        when(mapper.insertHistory(anyMap())).thenThrow(new IllegalStateException("SQL password=secret"));
        var result = mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                .contentType(MediaType.APPLICATION_JSON).content(payload(",\"attr1_val\":\"250000\"")))
            .andExpect(status().isInternalServerError()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("requestId").asText()).isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        assertThat(body.toString()).doesNotContain("SQL", "password", "secret");
    }

    @Test
    void durableContractFixtureIsLoadedFromClasspath() throws Exception {
        try (var stream = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            String contract = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(contract).contains("operationId: getCodeGroups", "operationId: saveCodes",
                "operationId: getCodeHistory", "operationId: createCodeGroup", "operationId: getAdminCodes");
        }
    }

    private void noWrites() {
        verify(mapper, never()).insertCode(anyMap());
        verify(mapper, never()).updateCode(anyMap());
        verify(mapper, never()).insertHistory(anyMap());
    }
}
