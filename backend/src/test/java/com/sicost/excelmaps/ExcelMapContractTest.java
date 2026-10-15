package com.sicost.excelmaps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.CurrentUser;
import com.sicost.common.GlobalExceptionHandler;
import com.sicost.common.PermissionService;
import com.sicost.common.RequestIdFilter;
import com.sicost.persistence.AuditContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.yaml.snakeyaml.Yaml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 controller/service와 상태 보유 mapper 경계로 HTTP 계약을 검증한다. SQL 원자성은 별도 DB 시험이다. */
class ExcelMapContractTest {
    private ExcelMapMapper mapper;
    private MockMvc mvc;
    private ExcelMapRow stored;
    private final ObjectMapper json = new ObjectMapper();
    private final List<List<Object>> history = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mapper = mock(ExcelMapMapper.class);
        var service = new ExcelMapService(mapper, new AuditContext(new CurrentUser()), new PermissionService());
        mvc = MockMvcBuilders.standaloneSetup(new ExcelMapController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
        stored = new ExcelMapRow();
        stored.setMapId(19L);
        stored.setExcelLblNm("테스트표기");
        stored.setTrnsCdGrpId("JOB");
        stored.setTrnsCd("BE");
        stored.setMapRoleCd("MAIN");
        stored.setRmrkDesc("기존 메모");
        stored.setUseYn("Y");
        stored.setCdGrpNm("직무");
        stored.setCdNm("백엔드");
        when(mapper.selectAll()).thenAnswer(call -> List.of(stored));
        when(mapper.selectById(19L)).thenAnswer(call -> stored);
        when(mapper.activeTarget("JOB", "BE")).thenReturn(1);
        when(mapper.activeTarget("JOB", "OPS")).thenReturn(1);
        when(mapper.insert(any())).thenAnswer(call -> {
            stored = call.getArgument(0);
            stored.setMapId(31L);
            stored.setCdGrpNm("직무");
            stored.setCdNm("백엔드");
            return 1;
        });
        when(mapper.update(eq(19L), anyMap())).thenAnswer(call -> {
            Map<String, String> changes = call.getArgument(1);
            if (changes.containsKey("map_role_cd")) stored.setMapRoleCd(changes.get("map_role_cd"));
            if (changes.containsKey("rmrk_desc")) stored.setRmrkDesc(changes.get("rmrk_desc"));
            if (changes.containsKey("use_yn")) stored.setUseYn(changes.get("use_yn"));
            return 1;
        });
        when(mapper.history(any(), anyString(), anyString(), nullable(String.class),
            nullable(String.class), nullable(Long.class))).thenAnswer(call -> {
                history.add(java.util.Arrays.asList(call.getArguments()));
                return 1;
            });
    }

    @Test
    void publicListIsTypedOnceAndDoesNotWrite() throws Exception {
        mvc.perform(get("/api/admin/excel-maps"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].map_id").value(19))
            .andExpect(jsonPath("$.data[0].cd_grp_nm").value("직무"))
            .andExpect(jsonPath("$.data[0].cd_nm").value("백엔드"))
            .andExpect(jsonPath("$.data[0].rmrk_desc").value("기존 메모"));
        verify(mapper).selectAll();
        verifyNoMoreInteractions(mapper);
        assertThat(history).isEmpty();
    }

    @Test
    void publicCreateUsesGeneratedKeyAndNullActorThenGetShowsCommittedInput() throws Exception {
        mvc.perform(post("/api/admin/excel-maps").contentType(MediaType.APPLICATION_JSON).content(validCreate("BE")))
            .andExpect(status().isNoContent()).andExpect(content().bytes(new byte[0]))
            .andExpect(header().exists("X-Request-Id"));
        mvc.perform(get("/api/admin/excel-maps"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].map_id").value(31))
            .andExpect(jsonPath("$.data[0].use_yn").value("Y"))
            .andExpect(jsonPath("$.data[0].map_role_cd").value("MAIN"));
        assertThat(stored.getCreatedBy()).isNull();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).subList(1, 6))
            .containsExactly("ADD", "excel_map.31", null, "테스트표기", null);
    }

    @Test
    void sameLabelWithDifferentTargetIsAllowed() throws Exception {
        mvc.perform(post("/api/admin/excel-maps").contentType(MediaType.APPLICATION_JSON).content(validCreate("OPS")))
            .andExpect(status().isNoContent());
        assertThat(stored.getTrnsCd()).isEqualTo("OPS");
        assertThat(history).hasSize(1);
    }

    @Test
    void duplicateHasStableBusinessCodeAndDoesNotWriteHistory() throws Exception {
        doThrow(new DuplicateKeyException("private SQL")).when(mapper).insert(any());
        mvc.perform(post("/api/admin/excel-maps").contentType(MediaType.APPLICATION_JSON).content(validCreate("BE")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"))
            .andExpect(jsonPath("$.message").value("이미 있는 대응입니다."));
        assertThat(stored.getMapId()).isEqualTo(19L);
        assertThat(history).isEmpty();
    }

    static Stream<Arguments> invalidCreate() {
        return Stream.of(
            Arguments.of("{\"excel_lbl_nm\":\"\"}", "excel_lbl_nm", "엑셀 글자를 입력하세요."),
            Arguments.of("{\"excel_lbl_nm\":\"표기\"}", "trns_cd_grp_id", "대응할 코드를 고르세요."),
            Arguments.of("{\"excel_lbl_nm\":\"표기\",\"trns_cd_grp_id\":\"JOB\"}",
                "trns_cd", "대응할 코드를 고르세요."),
            Arguments.of(validCreate("BE").replace("MAIN", "OTHER"), "map_role_cd", "주/보조를 선택하세요."),
            Arguments.of(validCreate("BE").replace("null", "\"" + "가".repeat(201) + "\""),
                "rmrk_desc", "메모는 200자 이하로 입력하세요.")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCreate")
    void invalidCreateLeavesRowsAndHistoryUnchanged(String body, String field, String message) throws Exception {
        mvc.perform(post("/api/admin/excel-maps").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field))
            .andExpect(jsonPath("$.message").value(message));
        verify(mapper, never()).insert(any());
        assertThat(history).isEmpty();
    }

    @Test
    void missingOrInactiveCodeCannotBeSelected() throws Exception {
        when(mapper.activeTarget("JOB", "BE")).thenReturn(0);
        mvc.perform(post("/api/admin/excel-maps").contentType(MediaType.APPLICATION_JSON).content(validCreate("BE")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("trns_cd"));
        verify(mapper, never()).insert(any());
        assertThat(history).isEmpty();
    }

    @Test
    void patchChangesOnlyEditableFieldsAndAppendsEveryBeforeAndAfter() throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 19).contentType(MediaType.APPLICATION_JSON)
            .content("{\"map_role_cd\":\"SUB\",\"rmrk_desc\":\"수정\"}"))
            .andExpect(status().isNoContent()).andExpect(content().bytes(new byte[0]));
        mvc.perform(get("/api/admin/excel-maps"))
            .andExpect(jsonPath("$.data[0].map_id").value(19))
            .andExpect(jsonPath("$.data[0].excel_lbl_nm").value("테스트표기"))
            .andExpect(jsonPath("$.data[0].trns_cd_grp_id").value("JOB"))
            .andExpect(jsonPath("$.data[0].trns_cd").value("BE"))
            .andExpect(jsonPath("$.data[0].map_role_cd").value("SUB"))
            .andExpect(jsonPath("$.data[0].rmrk_desc").value("수정"));
        assertThat(history).hasSize(2);
        assertThat(history.get(0).subList(1, 6))
            .containsExactly("UPDATE", "excel_map.19.map_role_cd", "MAIN", "SUB", null);
        assertThat(history.get(1).subList(1, 6))
            .containsExactly("UPDATE", "excel_map.19.rmrk_desc", "기존 메모", "수정", null);
    }

    @Test
    void patchMemoNullClearsButAbsentRoleAndUseAreRetained() throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 19).contentType(MediaType.APPLICATION_JSON)
            .content("{\"rmrk_desc\":null}"))
            .andExpect(status().isNoContent());
        assertThat(stored.getRmrkDesc()).isNull();
        assertThat(stored.getMapRoleCd()).isEqualTo("MAIN");
        assertThat(stored.getUseYn()).isEqualTo("Y");
        assertThat(history.get(0).get(4)).isNull();
    }

    @Test
    void disableThenEnableKeepsIdentityAndWritesStateHistory() throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 19).contentType(MediaType.APPLICATION_JSON)
            .content("{\"use_yn\":\"N\"}"))
            .andExpect(status().isNoContent());
        assertThat(stored.getUseYn()).isEqualTo("N");
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 19).contentType(MediaType.APPLICATION_JSON)
            .content("{\"use_yn\":\"Y\"}"))
            .andExpect(status().isNoContent());
        assertThat(stored.getMapId()).isEqualTo(19L);
        assertThat(history.stream().map(row -> row.get(1))).containsExactly("DISABLE", "ENABLE");
    }

    @Test
    void noopDoesNotUpdateOrAudit() throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 19).contentType(MediaType.APPLICATION_JSON)
            .content("{\"map_role_cd\":\"MAIN\"}"))
            .andExpect(status().isNoContent());
        verify(mapper, never()).update(anyLong(), anyMap());
        assertThat(history).isEmpty();
    }

    static Stream<Arguments> invalidPatch() {
        return Stream.of(
            Arguments.of("{\"map_role_cd\":\"OTHER\"}", "map_role_cd"),
            Arguments.of("{\"map_role_cd\":null}", "map_role_cd"),
            Arguments.of("{\"use_yn\":null}", "use_yn"),
            Arguments.of("{\"rmrk_desc\":\"" + "가".repeat(201) + "\"}", "rmrk_desc"),
            Arguments.of("{\"excel_lbl_nm\":\"테스트표기\"}", "excel_lbl_nm"),
            Arguments.of("{\"map_id\":19}", "map_id"),
            Arguments.of("{\"trns_cd_grp_id\":\"JOB\"}", "trns_cd_grp_id"),
            Arguments.of("{\"trns_cd\":\"BE\"}", "trns_cd"),
            Arguments.of("{}", "body"),
            Arguments.of("{", "body"),
            Arguments.of("{\"rmrk_desc\":9}", "rmrk_desc")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidPatch")
    void invalidPatchReturnsFieldAndNeverWrites(String body, String field) throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 19).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        verify(mapper, never()).update(anyLong(), anyMap());
        assertThat(history).isEmpty();
    }

    @Test
    void unknownMapReturns404WithoutWriting() throws Exception {
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", 987).contentType(MediaType.APPLICATION_JSON)
            .content("{\"rmrk_desc\":\"수정\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.field").value("map_id"));
        assertThat(history).isEmpty();
    }

    @Test
    void persistenceFailureDoesNotLeakInternalDetails() throws Exception {
        when(mapper.selectAll()).thenThrow(new IllegalStateException("SQL password=private jdbc:postgresql://secret"));
        var result = mvc.perform(get("/api/admin/excel-maps"))
            .andExpect(status().isInternalServerError()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password", "jdbc", "SQL");
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("requestId").asText()).isEqualTo(result.getResponse().getHeader("X-Request-Id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void durableOpenApiDeclaresTheOperationsExercisedHere() throws Exception {
        try (var stream = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            Map<String, Object> contract = new Yaml().load(stream);
            Map<String, Map<String, Map<String, Object>>> paths = (Map) contract.get("paths");
            assertThat(paths.get("/api/admin/excel-maps").get("get").get("operationId"))
                .isEqualTo("getExcelMaps");
            assertThat(paths.get("/api/admin/excel-maps").get("post").get("operationId"))
                .isEqualTo("createExcelMap");
            assertThat(paths.get("/api/admin/excel-maps/{map_id}").get("patch").get("operationId"))
                .isEqualTo("updateExcelMap");
        }
        mvc.perform(get("/api/admin/excel-maps")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].map_role_cd").value("MAIN"));
    }

    private static String validCreate(String code) {
        return "{\"excel_lbl_nm\":\"테스트표기\",\"trns_cd_grp_id\":\"JOB\",\"trns_cd\":\"" + code
            + "\",\"map_role_cd\":\"MAIN\",\"rmrk_desc\":null}";
    }
}
