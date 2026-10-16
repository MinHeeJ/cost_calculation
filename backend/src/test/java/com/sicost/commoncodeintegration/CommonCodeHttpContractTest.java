package com.sicost.commoncodeintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.apiops.PublicLookupController;
import com.sicost.apiops.PublicLookupMapper;
import com.sicost.apiops.PublicLookupService;
import com.sicost.codegroups.CodeGroupAdvice;
import com.sicost.codegroups.CodeGroupController;
import com.sicost.codegroups.CodeGroupMapper;
import com.sicost.codegroups.CodeGroupRow;
import com.sicost.codegroups.CodeGroupService;
import com.sicost.common.CurrentUser;
import com.sicost.common.GlobalExceptionHandler;
import com.sicost.common.PermissionService;
import com.sicost.common.RequestIdFilter;
import com.sicost.excelmaps.ExcelMapController;
import com.sicost.excelmaps.ExcelMapExceptionHandler;
import com.sicost.excelmaps.ExcelMapMapper;
import com.sicost.excelmaps.ExcelMapRow;
import com.sicost.excelmaps.ExcelMapService;
import com.sicost.operatingstandards.OperatingStandardAdvice;
import com.sicost.operatingstandards.OperatingStandardController;
import com.sicost.operatingstandards.OperatingStandardMapper;
import com.sicost.operatingstandards.OperatingStandardRow;
import com.sicost.operatingstandards.OperatingStandardService;
import com.sicost.persistence.AuditContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real feature handlers/services coexist; only persistence is doubled, not SQL durability. */
class CommonCodeHttpContractTest {
    private final ObjectMapper json = new ObjectMapper();
    private CodeGroupMapper groups;
    private ExcelMapMapper maps;
    private OperatingStandardMapper standards;
    private PublicLookupMapper lookups;
    private MockMvc mvc;
    private Map<String, Object> contract;

    @BeforeEach
    void setUp() throws Exception {
        groups = mock(CodeGroupMapper.class);
        maps = mock(ExcelMapMapper.class);
        standards = mock(OperatingStandardMapper.class);
        lookups = mock(PublicLookupMapper.class);
        var user = new CurrentUser();
        var audit = new AuditContext(user);
        var permissions = new PermissionService();
        mvc = MockMvcBuilders.standaloneSetup(
                new CodeGroupController(new CodeGroupService(groups, audit, permissions)),
                new ExcelMapController(new ExcelMapService(maps, audit, permissions)),
                new OperatingStandardController(new OperatingStandardService(standards, user, permissions)),
                new PublicLookupController(new PublicLookupService(lookups)))
            .setControllerAdvice(new CodeGroupAdvice(), new ExcelMapExceptionHandler(),
                new OperatingStandardAdvice(), new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
        try (var stream = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            contract = new Yaml().load(stream);
        }
    }

    @Test
    void publicGroupProjectionMatchesRequiredSchemaWithAllAdviceInstalled() throws Exception {
        var row = group("BIZ");
        row.setCdGrpNm("직무");
        row.setAttr1Nm("기본 단가(원)");
        row.setCodeCount(12);
        when(groups.selectGroups()).thenReturn(List.of(row));
        JsonNode data = success(get("/api/admin/code-groups"), "/api/admin/code-groups");
        requiredFields(data.get(0), "CodeGroupView");
        assertThat(data.get(0).path("code_count").asInt()).isEqualTo(12);
        assertThat(data.get(0).path("attr1_nm").asText()).isEqualTo("기본 단가(원)");
        verify(groups).selectGroups();
        verifyNoMoreInteractions(groups);
        verifyNoInteractions(maps, standards, lookups);
    }

    @Test
    void publicMapProjectionMatchesFrontendJoinedNamesAndNullMemo() throws Exception {
        var row = new ExcelMapRow();
        row.setMapId(9L);
        row.setExcelLblNm("개발");
        row.setTrnsCdGrpId("JOB");
        row.setTrnsCd("BE");
        row.setMapRoleCd("SUB");
        row.setUseYn("N");
        row.setCdGrpNm("직무");
        row.setCdNm("백엔드");
        when(maps.selectAll()).thenReturn(List.of(row));
        JsonNode data = success(get("/api/admin/excel-maps"), "/api/admin/excel-maps");
        requiredFields(data.get(0), "ExcelMapView");
        assertThat(data.get(0).path("use_yn").asText()).isEqualTo("N");
        assertThat(data.get(0).path("rmrk_desc").isNull()).isTrue();
        verify(maps).selectAll();
        verifyNoMoreInteractions(maps);
        verifyNoInteractions(groups, standards, lookups);
    }

    @Test
    void publicStandardsUseItemsAndClosedMonthWhileHistoryIsAnArray() throws Exception {
        var row = standard();
        when(standards.selectCurrent(anyString())).thenReturn(List.of(row));
        when(standards.selectLastClosed()).thenReturn("2026-09");
        when(standards.selectHistory("VAT_RATE")).thenReturn(List.of(row));
        JsonNode current = success(get("/api/admin/operating-standards"), "/api/admin/operating-standards");
        assertThat(current.path("last_closed_ym").asText()).isEqualTo("2026-09");
        requiredFields(current.path("items").get(0), "OperatingStandardView");
        JsonNode history = success(get("/api/admin/operating-standards/{oprt_std_id}/history", "VAT_RATE"),
            "/api/admin/operating-standards/{oprt_std_id}/history");
        assertThat(history.isArray()).isTrue();
        requiredFields(history.get(0), "OperatingStandardView");
        verifyNoInteractions(groups, maps, lookups);
    }

    @Test
    void anonymousGroupCreateUsesApproved204AndNullActor() throws Exception {
        noContent(post("/api/admin/code-groups").contentType("application/json")
            .content("{\"cd_grp_id\":\"CUSTOM\",\"cd_grp_nm\":\"업무\"}"), "/api/admin/code-groups", "post");
        verify(groups).insertGroup(argThat(v -> v.get("actor") == null));
        verify(groups).insertHistory(argThat(v -> v.get("actor") == null && v.get("code") == null));
        verifyNoInteractions(maps, standards, lookups);
    }

    @Test
    void anonymousFullListSaveDoesNotWrap204OrWriteNoOpHistory() throws Exception {
        when(groups.selectGroup("JOB")).thenReturn(group("BIZ"));
        noContent(put("/api/admin/code-groups/{group}/codes", "JOB").contentType("application/json")
            .content("{\"codes\":[]}"), "/api/admin/code-groups/{group}/codes", "put");
        verify(groups, never()).insertHistory(anyMap());
        verify(groups, never()).updateCode(anyMap());
        verifyNoInteractions(maps, standards, lookups);
    }

    @Test
    void anonymousMapCreateDoesNotUseAnotherFeaturesAdviceOrActor() throws Exception {
        when(maps.selectCodeUse("JOB", "BE")).thenReturn("Y");
        when(maps.insert(any(), isNull())).thenAnswer(call -> {
            ExcelMapRow row = call.getArgument(0);
            row.setMapId(9L);
            return 1;
        });
        noContent(post("/api/admin/excel-maps").contentType("application/json").content("""
            {"excel_lbl_nm":"개발","trns_cd_grp_id":"JOB","trns_cd":"BE","map_role_cd":"SUB"}
            """), "/api/admin/excel-maps", "post");
        verify(maps).insertHistory(any(), eq("ADD"), eq("excel_map.9"), isNull(), eq("개발"), isNull());
        verifyNoInteractions(groups, standards, lookups);
    }

    @Test
    void anonymousMapPatchHasEmpty204AndPreservesIdentity() throws Exception {
        var row = new ExcelMapRow();
        row.setMapId(9L);
        row.setTrnsCdGrpId("JOB");
        row.setTrnsCd("BE");
        row.setUseYn("Y");
        when(maps.selectById(9L)).thenReturn(row);
        when(maps.update(any())).thenReturn(1);
        noContent(patch("/api/admin/excel-maps/{map_id}", 9).contentType("application/json")
            .content("{\"use_yn\":\"N\"}"), "/api/admin/excel-maps/{map_id}", "patch");
        verify(maps).insertHistory(same(row), eq("DISABLE"), eq("excel_map.9.use_yn"), eq("Y"), eq("N"), isNull());
        assertThat(row.getTrnsCd()).isEqualTo("BE");
        verifyNoInteractions(groups, standards, lookups);
    }

    @Test
    void anonymousStandardWriteHasEmpty204AndParamHistoryIdentity() throws Exception {
        when(standards.selectHistory("VAT_RATE")).thenReturn(List.of(standard()));
        when(standards.selectLastClosed()).thenReturn("2026-09");
        noContent(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType("application/json").content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11}"),
            "/api/admin/operating-standards/{oprt_std_id}", "put");
        verify(standards).insertHistory("VAT_RATE", "2026-11", "10", "11", null);
        verifyNoInteractions(groups, maps, lookups);
    }

    @Test
    void codeGroupDuplicateRetainsBusinessCodeWithAllAdviceRegistered() throws Exception {
        when(groups.selectGroup("JOB")).thenReturn(group("BIZ"));
        error(post("/api/admin/code-groups").contentType("application/json")
            .content("{\"cd_grp_id\":\"JOB\",\"cd_grp_nm\":\"직무\"}"), 409, "DUPLICATE_CODE");
        verify(groups, never()).insertGroup(anyMap());
        verifyNoInteractions(maps, standards, lookups);
    }

    @Test
    void systemCodeAdditionRetainsLockedCodeInsteadOfGeneric500() throws Exception {
        when(groups.selectGroup("CONTRACT_TYPE")).thenReturn(group("SYSTEM"));
        error(put("/api/admin/code-groups/{group}/codes", "CONTRACT_TYPE")
            .contentType("application/json").content("""
                {"codes":[{"dtl_cd":"NEW","cd_nm":"추가","sort_seq":1,"use_yn":"Y"}]}
                """), 409, "SYSTEM_CODE_LOCKED");
        verify(groups, never()).insertCode(anyMap());
        verify(groups, never()).insertHistory(anyMap());
    }

    @Test
    void closedStandardRetainsClosedMonthCodeInsteadOfOtherAdvice() throws Exception {
        when(standards.selectHistory("VAT_RATE")).thenReturn(List.of(standard()));
        when(standards.selectLastClosed()).thenReturn("2026-09");
        error(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType("application/json").content("{\"aply_bgn_ym\":\"2026-09\",\"num_val\":11}"),
            409, "CLOSED_MONTH");
        verify(standards, never()).upsert(any(), any());
        verify(standards, never()).insertHistory(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void absentMapRetains404AndFieldWithAllAdviceRegistered() throws Exception {
        error(patch("/api/admin/excel-maps/{map_id}", 99).contentType("application/json")
            .content("{\"use_yn\":\"N\"}"), 404, "404");
        verify(maps, never()).update(any());
        verifyNoInteractions(groups, standards, lookups);
    }

    @Test
    void publicLookupLocalNotFoundHandlerStillWins() throws Exception {
        error(get("/api/codes/{group}", "ABSENT"), 404, "404");
        verify(lookups).groupExists("ABSENT");
        verifyNoMoreInteractions(lookups);
        verifyNoInteractions(groups, maps, standards);
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/admin/code-groups", "PUT,/api/admin/code-groups/JOB/codes",
        "POST,/api/admin/excel-maps", "PATCH,/api/admin/excel-maps/9",
        "PUT,/api/admin/operating-standards/VAT_RATE"})
    void malformedJsonIs400AcrossEveryWrite(String method, String path) throws Exception {
        error(request(org.springframework.http.HttpMethod.valueOf(method), path)
            .contentType("application/json").content("{"), 400, "400");
        verifyNoInteractions(groups, maps, standards, lookups);
    }

    @Test
    void unexpectedMapperFailureUsesSafeShared500() throws Exception {
        when(maps.selectAll()).thenThrow(new IllegalStateException("SQL internal-connection-detail"));
        var result = error(get("/api/admin/excel-maps"), 500, "500");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQL", "internal-connection-detail");
    }

    private CodeGroupRow group(String type) {
        var row = new CodeGroupRow();
        row.setCdGrpId("JOB");
        row.setCdGrpSeCd(type);
        row.setUseYn("Y");
        return row;
    }

    private OperatingStandardRow standard() {
        var row = new OperatingStandardRow();
        row.setOprtStdId("VAT_RATE");
        row.setAplyBgnYm("2000-01");
        row.setOprtStdNm("부가세율");
        row.setNumVal(new BigDecimal("10"));
        row.setUnitNm("%");
        return row;
    }

    private JsonNode success(MockHttpServletRequestBuilder request, String path) throws Exception {
        operation(path, "get", "200");
        var result = mvc.perform(request).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.data").doesNotExist()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("meta").path("requestId").asText())
            .isEqualTo(result.getResponse().getHeader("X-Request-Id")).isNotBlank();
        return body.path("data");
    }

    private void noContent(MockHttpServletRequestBuilder request, String path, String method) throws Exception {
        operation(path, method, "204");
        mvc.perform(request).andExpect(status().isNoContent()).andExpect(content().bytes(new byte[0]))
            .andExpect(header().exists("X-Request-Id"));
    }

    private MvcResult error(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        var result = mvc.perform(request).andExpect(status().is(status))
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.code").value(code)).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        requiredFields(body, "ApiError");
        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(id).isNotBlank();
        assertThat(body.path("requestId").asText()).isEqualTo(id);
        assertThat(body.path("meta").path("requestId").asText()).isEqualTo(id);
        assertThat(body.has("data")).isFalse();
        return result;
    }

    @SuppressWarnings("unchecked")
    private void requiredFields(JsonNode actual, String schema) {
        var components = (Map<String, Object>) contract.get("components");
        var schemas = (Map<String, Object>) components.get("schemas");
        var definition = (Map<String, Object>) schemas.get(schema);
        for (String field : (List<String>) definition.get("required")) {
            assertThat(actual.has(field)).as("%s.%s present even when null", schema, field).isTrue();
        }
    }

    @SuppressWarnings("unchecked")
    private void operation(String path, String method, String status) {
        var paths = (Map<String, Object>) contract.get("paths");
        var item = (Map<String, Object>) paths.get(path);
        assertThat(item).as("durable contract path %s", path).isNotNull();
        var operation = (Map<String, Object>) item.get(method);
        assertThat(operation.get("security")).isEqualTo(List.of());
        assertThat((Map<String, Object>) operation.get("responses")).containsKey(status);
    }
}
