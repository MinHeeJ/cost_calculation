package com.sicost.partnersops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.CurrentUser;
import com.sicost.common.GlobalExceptionHandler;
import com.sicost.common.PermissionService;
import com.sicost.common.RequestIdFilter;
import com.sicost.persistence.AuditContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller/service, stateful persistence boundary like ExcelMapContractTest; not SQL durability evidence. */
class PartnerActivityContractTest {
    private PartnerActivityMapper mapper;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Long, PartnerStatusRow> rows = new LinkedHashMap<>();
    private final List<PartnerHistoryRow> changes = new ArrayList<>();
    private final LocalDate today = LocalDate.of(2026, 10, 16);

    @BeforeEach
    void setUp() {
        mapper = mock(PartnerActivityMapper.class);
        var service = new PartnerActivityService(mapper, new AuditContext(new CurrentUser()),
            new PermissionService(), Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("UTC")));
        mvc = MockMvcBuilders.standaloneSetup(new PartnerActivityController(service))
            .setControllerAdvice(new PartnerActivityExceptionHandler(), new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
        rows.clear();
        changes.clear();
        partner(1, "가 고객", "CUST", "Y");
        partner(2, "나 협력", "VEND", "Y");
        partner(3, "다 겸업", "BOTH", "Y");
        partner(4, "라 중지", "CUST", "N");
        when(mapper.selectPartner(anyLong())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(mapper.updateUse(anyLong(), anyString(), isNull())).thenAnswer(call -> {
            rows.get(call.getArgument(0)).setUseYn(call.getArgument(1));
            return 1;
        });
        when(mapper.selectOptions(anyString())).thenAnswer(call -> rows.values().stream()
            .filter(row -> row.getUseYn().equals("Y"))
            .filter(row -> row.getPtnrSeCd().equals("BOTH") || row.getPtnrSeCd().equals(call.getArgument(0)))
            .sorted(java.util.Comparator.comparing(PartnerStatusRow::getPtnrNm))
            .map(row -> {
                var option = new PartnerOptionRow();
                option.setId(row.getId());
                option.setNo(row.getNo());
                option.setName(row.getPtnrNm());
                return option;
            }).toList());
        when(mapper.selectAssignments(anyLong(), any())).thenReturn(List.of());
        when(mapper.selectHistory(anyLong())).thenAnswer(call -> List.copyOf(changes));
        when(mapper.insertHistory(anyLong(), anyString(), any(), any(), any(), isNull())).thenAnswer(call -> {
            var item = new PartnerHistoryRow();
            item.setChgAt(LocalDateTime.of(2026, 10, 16, 10, 0));
            item.setDataChgTyCd(call.getArgument(1));
            item.setClmnNm(call.getArgument(2));
            item.setBfrVal(call.getArgument(3));
            item.setAftVal(call.getArgument(4));
            changes.add(0, item);
            return 1;
        });
    }

    private PartnerStatusRow partner(long id, String name, String kind, String use) {
        var row = new PartnerStatusRow();
        row.setId(id);
        row.setNo("C-00" + id);
        row.setPtnrNm(name);
        row.setPtnrSeCd(kind);
        row.setKindName(kind);
        row.setBizRegNo("123-45-67890");
        row.setUseYn(use);
        row.setBankCd("KB");
        row.setBankName("국민은행");
        row.setAcntNo("123456789012");
        row.setDpstrNm(name);
        row.setTxinvEmailAddr("test@example.com");
        rows.put(id, row);
        return row;
    }

    @Test
    void publicOptionsAreStrictThreeFieldProjectionsWithoutWrites() throws Exception {
        var result = mvc.perform(get("/api/partners/options").param("kind", "CUST"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[1].name").value("다 겸업"))
            .andExpect(jsonPath("$.data[0].no").value("C-001"))
            .andExpect(header().exists("X-Request-Id")).andReturn();
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("data").get(0).size()).isEqualTo(3);
        assertThat(body.at("/meta/requestId").asText())
            .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        verify(mapper).selectOptions("CUST");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void optionsMissingKindIsFieldErrorWithoutPersistence() throws Exception {
        mvc.perform(get("/api/partners/options"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("kind"));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOTH", "", "OTHER"})
    void optionsRejectInvalidKind(String kind) throws Exception {
        mvc.perform(get("/api/partners/options").param("kind", kind))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("kind"));
        verifyNoInteractions(mapper);
    }

    @Test
    void assignmentsUseOneSeoulDateAndAreReadOnly() throws Exception {
        var item = new PartnerAssignmentRow();
        item.setProjectName("고객 프로젝트");
        item.setDeveloperName("김개발");
        item.setAffiliationName("나 협력");
        item.setJobName("백엔드");
        item.setAssignmentRate(new java.math.BigDecimal("0.50"));
        item.setEndDate(today);
        when(mapper.selectAssignments(2, today)).thenReturn(List.of(item));
        mvc.perform(get("/api/partners/{id}/assignments", 2))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
            .andExpect(jsonPath("$.data[0].affiliationName").value("나 협력"))
            .andExpect(jsonPath("$.data[0].assignmentRate").value(0.5));
        verify(mapper).selectPartner(2);
        verify(mapper).selectAssignments(2, today);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void assignmentsEmptyIsAnArray() throws Exception {
        mvc.perform(get("/api/partners/{id}/assignments", 1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verify(mapper, never()).updateUse(anyLong(), anyString(), any());
    }

    @Test
    void assignmentsMissingPartnerDoesNotReadRelations() throws Exception {
        mvc.perform(get("/api/partners/{id}/assignments", 999))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        verify(mapper, never()).selectAssignments(anyLong(), any());
    }

    @Test
    void assignmentsMalformedIdHasIdField() throws Exception {
        mvc.perform(get("/api/partners/{id}/assignments", "bad"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));
        verifyNoInteractions(mapper);
    }

    @Test
    void historyReturnsOldNewAndNullActorWithoutMutating() throws Exception {
        var item = new PartnerHistoryRow();
        item.setChgAt(LocalDateTime.of(2026, 10, 16, 10, 0));
        item.setDataChgTyCd("UPDATE");
        item.setClmnNm("acnt_no");
        item.setBfrVal("1234");
        item.setAftVal("5678");
        changes.add(item);
        mvc.perform(get("/api/partners/{id}/history", 1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
            .andExpect(jsonPath("$.data[0].clmnNm").value("acnt_no"))
            .andExpect(jsonPath("$.data[0].bfrVal").value("1234"))
            .andExpect(jsonPath("$.data[0].aftVal").value("5678"))
            .andExpect(jsonPath("$.data[0].chgUserId").isEmpty());
        assertThat(changes).containsExactly(item);
        verify(mapper).selectPartner(1);
        verify(mapper).selectHistory(1);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void historyEmptyIsAnArray() throws Exception {
        mvc.perform(get("/api/partners/{id}/history", 1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void historyMissingPartnerIs404() throws Exception {
        mvc.perform(get("/api/partners/{id}/history", 999))
            .andExpect(status().isNotFound());
        verify(mapper, never()).selectHistory(anyLong());
    }

    @Test
    void historyMalformedIdHasIdField() throws Exception {
        mvc.perform(get("/api/partners/{id}/history", "bad"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));
        verifyNoInteractions(mapper);
    }

    @Test
    void disableDeduplicatesBeforeWritesAndOptionsReflectActualState() throws Exception {
        mvc.perform(post("/api/partners/disable").contentType("application/json")
            .content("{\"ids\":[1,1,3]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
            .andExpect(jsonPath("$.data.disabledIds[0]").value(1))
            .andExpect(jsonPath("$.data.disabledIds[1]").value(3))
            .andExpect(jsonPath("$.data.disabledCount").value(2));
        assertThat(rows.get(1L).getUseYn()).isEqualTo("N");
        assertThat(changes).hasSize(2).allSatisfy(item -> {
            assertThat(item.getDataChgTyCd()).isEqualTo("DISABLE");
            assertThat(item.getClmnNm()).isNull();
            assertThat(item.getBfrVal()).isNull();
            assertThat(item.getAftVal()).isNull();
        });
        verify(mapper).updateUse(1, "N", null);
        mvc.perform(get("/api/partners/options").param("kind", "CUST"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"ids\":[]}", "{\"ids\":null}", "{\"ids\":[1.2]}",
        "{\"ids\":[null]}", "{\"ids\":[\"1\"]}", "null", "[]", "{", "{\"ids\":[1],\"other\":1}"})
    void disableInvalidInputNeverTouchesPersistence(String input) throws Exception {
        mvc.perform(post("/api/partners/disable").contentType("application/json").content(input))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("ids"));
        verifyNoInteractions(mapper);
    }

    @Test
    void disablePreflightsAllIdsBeforeAnyChange() throws Exception {
        mvc.perform(post("/api/partners/disable").contentType("application/json").content("{\"ids\":[1,999]}"))
            .andExpect(status().isNotFound());
        assertThat(rows.get(1L).getUseYn()).isEqualTo("Y");
        assertThat(changes).isEmpty();
        verify(mapper, never()).updateUse(anyLong(), anyString(), any());
        verify(mapper, never()).countOngoingProjects(anyLong(), any());
    }

    @Test
    void disablePartialSuccessRetainsExcludedRelationsAndAuditsOnlyChanged() throws Exception {
        when(mapper.countOngoingProjects(2, today)).thenReturn(2);
        mvc.perform(post("/api/partners/disable").contentType("application/json").content("{\"ids\":[1,2,3]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.disabledCount").value(2))
            .andExpect(jsonPath("$.data.inUseCount").value(1))
            .andExpect(jsonPath("$.data.excluded[0].reason").value("진행 프로젝트 2건"));
        assertThat(rows.get(2L).getUseYn()).isEqualTo("Y");
        assertThat(changes).hasSize(2);
        verify(mapper, never()).updateUse(eq(2L), anyString(), any());
    }

    @Test
    void disableSingleInUseReturnsStructured409AndNoWrites() throws Exception {
        when(mapper.countAffiliatedDevelopers(1, today)).thenReturn(3);
        var result = mvc.perform(post("/api/partners/disable").contentType("application/json")
            .content("{\"ids\":[1,1]}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PARTNER_IN_USE"))
            .andExpect(jsonPath("$.excluded[0].reason").value("소속 인력 3명"))
            .andExpect(jsonPath("$.excluded[0].affiliatedDeveloperCount").value(3)).andReturn();
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("requestId").asText()).isEqualTo(body.at("/meta/requestId").asText())
            .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        assertThat(rows.get(1L).getUseYn()).isEqualTo("Y");
        verify(mapper, never()).updateUse(anyLong(), anyString(), any());
    }

    @Test
    void disableAlreadyInactiveTakesPrecedenceOverBusinessCounts() throws Exception {
        mvc.perform(post("/api/partners/disable").contentType("application/json").content("{\"ids\":[4,1]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.disabledCount").value(1))
            .andExpect(jsonPath("$.data.alreadyDisabledCount").value(1));
        verify(mapper, never()).countOngoingProjects(eq(4L), any());
        verify(mapper, never()).countAffiliatedDevelopers(eq(4L), any());
    }

    @Test
    void disableAllAlreadyInactiveHasNoDml() throws Exception {
        mvc.perform(post("/api/partners/disable").contentType("application/json").content("{\"ids\":[4]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.disabledCount").value(0))
            .andExpect(jsonPath("$.data.alreadyDisabledCount").value(1));
        verify(mapper, never()).updateUse(anyLong(), anyString(), any());
    }

    @Test
    void disableAllInUseMultipleStillReturns200() throws Exception {
        when(mapper.countOngoingProjects(anyLong(), any())).thenReturn(2);
        mvc.perform(post("/api/partners/disable").contentType("application/json").content("{\"ids\":[1,2]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.disabledCount").value(0))
            .andExpect(jsonPath("$.data.inUseCount").value(2))
            .andExpect(jsonPath("$.data.excluded.length()").value(2));
        verify(mapper, never()).updateUse(anyLong(), anyString(), any());
    }

    @Test
    void enableWritesExactAuditAndRepeatedEnableDoesNotWrite() throws Exception {
        mvc.perform(post("/api/partners/{id}/enable", 4))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
            .andExpect(jsonPath("$.data.useYn").value("Y"))
            .andExpect(jsonPath("$.data.no").value("C-004"))
            .andExpect(jsonPath("$.data.bankCd").doesNotExist())
            .andExpect(jsonPath("$.data.acntNo").doesNotExist());
        verify(mapper).insertHistory(4, "UPDATE", "use_yn", "N", "Y", null);
        mvc.perform(get("/api/partners/options").param("kind", "CUST"))
            .andExpect(jsonPath("$.data[2].id").value(4));
        clearInvocations(mapper);
        mvc.perform(post("/api/partners/{id}/enable", 4)).andExpect(status().isOk());
        verify(mapper).selectPartner(4);
        verifyNoMoreInteractions(mapper);
        assertThat(changes).hasSize(1);
    }

    @Test
    void enableVendorKeepsAccountAndOmitsEmail() throws Exception {
        rows.get(2L).setUseYn("N");
        mvc.perform(post("/api/partners/{id}/enable", 2))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.acntNo").value("123456789012"))
            .andExpect(jsonPath("$.data.bankName").value("국민은행"))
            .andExpect(jsonPath("$.data.txinvEmailAddr").doesNotExist());
    }

    @Test
    void enableMissingPartnerIs404() throws Exception {
        mvc.perform(post("/api/partners/{id}/enable", 999)).andExpect(status().isNotFound());
        verify(mapper, never()).updateUse(anyLong(), anyString(), any());
    }

    @Test
    void enableMalformedIdHasIdField() throws Exception {
        mvc.perform(post("/api/partners/{id}/enable", "bad"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));
        verifyNoInteractions(mapper);
    }

    @Test
    void auditFailuresAreSanitized500NotFalseSuccess() throws Exception {
        when(mapper.insertHistory(anyLong(), anyString(), any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("private SQL jdbc password"));
        var result = mvc.perform(post("/api/partners/disable").contentType("application/json")
            .content("{\"ids\":[1]}"))
            .andExpect(status().isInternalServerError()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private", "SQL", "jdbc", "password");
    }

    @Test
    @SuppressWarnings("unchecked")
    void durableContractFixtureDeclaresExactOperationsExercisedByHttpTests() throws Exception {
        Map<String, Object> contract;
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            contract = new Yaml().load(input);
        }
        var paths = (Map<String, Map<String, Map<String, Object>>>) contract.get("paths");
        assertThat(paths.get("/api/partners/options").get("get").get("operationId"))
            .isEqualTo("getPartnerOptions");
        assertThat(paths.get("/api/partners/{id}/assignments").get("get").get("operationId"))
            .isEqualTo("getPartnerAssignments");
        assertThat(paths.get("/api/partners/{id}/history").get("get").get("operationId"))
            .isEqualTo("getPartnerHistory");
        assertThat(paths.get("/api/partners/disable").get("post").get("operationId"))
            .isEqualTo("disablePartners");
        assertThat(paths.get("/api/partners/{id}/enable").get("post").get("operationId"))
            .isEqualTo("enablePartner");
        mvc.perform(get("/api/partners/options").param("kind", "VEND"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(2));
    }
}
