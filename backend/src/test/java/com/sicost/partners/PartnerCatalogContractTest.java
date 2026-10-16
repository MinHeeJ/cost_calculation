package com.sicost.partners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.CurrentUser;
import com.sicost.common.GlobalExceptionHandler;
import com.sicost.common.PermissionService;
import com.sicost.common.RequestIdFilter;
import com.sicost.persistence.AuditContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller/service HTTP contract, with only the XML persistence port doubled. */
class PartnerCatalogContractTest {
    private final ObjectMapper json = new ObjectMapper();
    private PartnerCatalogMapper mapper;
    private MockMvc mvc;
    private PartnerCatalogRow stored;

    @BeforeEach
    void setup() {
        mapper = mock(PartnerCatalogMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T16:00:00Z"), ZoneId.of("Asia/Seoul"));
        var service = new PartnerCatalogService(mapper, new AuditContext(new CurrentUser()),
            new PermissionService(), clock);
        mvc = MockMvcBuilders.standaloneSetup(new PartnerCatalogController(service))
            .setControllerAdvice(new PartnerCatalogAdvice(), new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
        stored = new PartnerCatalogRow();
        stored.setId(7L);
        stored.setNo("C-007");
        stored.setPtnrNm("원래 상호");
        stored.setPtnrSeCd("CUST");
        stored.setBizRegNo("123-45-67890");
        stored.setTxinvEmailAddr("tax@example.test");
        stored.setUseYn("Y");
        when(mapper.selectById(7)).thenAnswer(call -> stored);
        when(mapper.nextId()).thenReturn(1000L);
        when(mapper.codeUse(anyString(), anyString())).thenReturn("Y");
        when(mapper.insert(any(), isNull())).thenAnswer(call -> {
            stored = call.getArgument(0);
            return 1;
        });
        when(mapper.selectById(1000)).thenAnswer(call -> stored);
        when(mapper.update(anyLong(), anyMap(), isNull())).thenAnswer(call -> {
            Map<String, String> changes = call.getArgument(1);
            var bean = new BeanWrapperImpl(stored);
            Map<String, String> names = Map.of(
                "ptnr_nm", "ptnrNm", "ptnr_se_cd", "ptnrSeCd", "txinv_email_addr", "txinvEmailAddr",
                "bank_cd", "bankCd", "acnt_no", "acntNo", "dpstr_nm", "dpstrNm"
            );
            changes.forEach((column, value) -> bean.setPropertyValue(names.get(column), value));
            return 1;
        });
    }

    private ObjectNode body(String kind) {
        ObjectNode body = json.createObjectNode();
        body.put("ptnrSeCd", kind).put("ptnrNm", "원래 상호").put("bizRegNo", "1234567890");
        if (!kind.equals("VEND")) {
            body.put("txinvEmailAddr", "tax@example.test");
        }
        if (!kind.equals("CUST")) {
            body.put("bankCd", "KB").put("acntNo", "1234-5678-9012").put("dpstrNm", "합성예금주");
        }
        return body;
    }

    @Test
    void publicCreateNormalizesIdentityAndWritesExactlyOneNullValuedInsertHistory() throws Exception {
        var result = mvc.perform(post("/api/partners").contentType("application/json").content(body("CUST").toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.no").value("C-1000"))
            .andExpect(jsonPath("$.data.bizRegNo").value("123-45-67890"))
            .andExpect(jsonPath("$.data.useYn").value("Y"))
            .andExpect(jsonPath("$.data.bankCd").doesNotExist()).andReturn();
        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(json.readTree(result.getResponse().getContentAsByteArray()).path("meta").path("requestId").asText())
            .isEqualTo(id).isNotBlank();
        verify(mapper).history(1000, "INSERT", null, null, null, null);
        mvc.perform(get("/api/partners/{id}", 1000))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.no").value("C-1000"));
    }

    @ParameterizedTest
    @CsvSource({"1,C-001", "999,C-999", "1000,C-1000", "10000,C-10000"})
    void identityCodeNeverTruncates(long id, String code) throws Exception {
        when(mapper.nextId()).thenReturn(id);
        when(mapper.selectById(id)).thenAnswer(call -> stored);
        mvc.perform(post("/api/partners").contentType("application/json").content(body("CUST").toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.no").value(code));
        assertThat(stored.getNo()).isEqualTo(code);
        verify(mapper).history(id, "INSERT", null, null, null, null);
    }

    @ParameterizedTest
    @CsvSource({"CUST,bizRegNo,123456789", "VEND,acntNo,", "BOTH,txinvEmailAddr,"})
    void requiredCreateValidationNeverWrites(String kind, String field, String value) throws Exception {
        var body = body(kind);
        body.put(field, value == null ? "" : value);
        mvc.perform(post("/api/partners").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field))
            .andExpect(jsonPath("$.success").value(false));
        verify(mapper, never()).insert(any(), any());
        verify(mapper, never()).history(anyLong(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"ptnrNm,51", "ceoNm,21", "chrgNm,21", "chrgTelNo,21", "dpstrNm,31", "txinvEmailAddr,101"})
    void apiLimitsAreNarrowerThanPhysicalColumns(String field, int length) throws Exception {
        var body = body("BOTH");
        body.put(field, "x".repeat(length));
        mvc.perform(post("/api/partners").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        verify(mapper, never()).insert(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"123 4567890", "abc1234567890", "12345678901", "123-456-7890"})
    void businessNumberDoesNotStripArbitraryCharacters(String number) throws Exception {
        var body = body("CUST");
        body.put("bizRegNo", number);
        mvc.perform(post("/api/partners").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("bizRegNo"));
    }

    @Test
    void duplicateIncludingDisabledPartnerUsesExistingNameAndNoWrites() throws Exception {
        when(mapper.duplicateName("123-45-67890")).thenReturn("중지된 상호");
        mvc.perform(post("/api/partners").contentType("application/json").content(body("CUST").toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_BIZ_NO"))
            .andExpect(jsonPath("$.field").value("bizRegNo"))
            .andExpect(jsonPath("$.message").value("이미 등록된 사업자번호입니다. (중지된 상호)"));
        verify(mapper, never()).nextId();
        verify(mapper, never()).insert(any(), any());
    }

    @Test
    void concurrentDuplicateUsesZeroAffectedRowsRatherThanAbortedTransaction() throws Exception {
        when(mapper.duplicateName("123-45-67890")).thenReturn(null, "경합 승자");
        when(mapper.insert(any(), isNull())).thenReturn(0);
        mvc.perform(post("/api/partners").contentType("application/json").content(body("CUST").toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("이미 등록된 사업자번호입니다. (경합 승자)"));
        verify(mapper, never()).history(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void irrelevantInputsAreIgnoredBeforeTheirTypesAreValidated() throws Exception {
        var body = body("CUST");
        body.putObject("bankCd");
        body.putArray("acntNo");
        body.put("dpstrNm", 5).put("useYn", "N").put("id", 99);
        mvc.perform(post("/api/partners").contentType("application/json").content(body.toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.bankCd").doesNotExist());
        assertThat(stored.getAcntNo()).isNull();
        assertThat(stored.getUseYn()).isEqualTo("Y");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUST", "VEND", "BOTH"})
    void publicDetailUsesActualOmissionAndAllowsInactivePartner(String kind) throws Exception {
        stored.setPtnrSeCd(kind);
        stored.setUseYn("N");
        stored.setBankCd("KB");
        stored.setAcntNo("1234-5678-9012");
        stored.setDpstrNm("합성예금주");
        var result = mvc.perform(get("/api/partners/{id}", 7))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.useYn").value("N")).andReturn();
        var data = json.readTree(result.getResponse().getContentAsByteArray()).path("data");
        assertThat(data.has("txinvEmailAddr")).isEqualTo(!kind.equals("VEND"));
        for (String field : List.of("bankCd", "acntNo", "dpstrNm", "bankName")) {
            assertThat(data.has(field)).isEqualTo(!kind.equals("CUST"));
        }
        if (!kind.equals("CUST")) {
            assertThat(data.path("acntNo").asText()).isEqualTo("1234-5678-9012");
        }
        verify(mapper).selectById(7);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void missingDetailIs404AndMalformedIdIsFieldError() throws Exception {
        mvc.perform(get("/api/partners/{id}", 888)).andExpect(status().isNotFound());
        mvc.perform(get("/api/partners/{id}", "bad"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));
    }

    @Test
    void publicListUsesSharedFiltersTotalDefaultFiftyAndSeoulDateWithoutWrites() throws Exception {
        when(mapper.count("VEND", "Y", "1234567890")).thenReturn(51L);
        mvc.perform(get("/api/partners").param("kind", "VEND").param("q", "1234567890"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(50)).andExpect(jsonPath("$.data.total").value(51))
            .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
        verify(mapper).list("VEND", "Y", "1234567890", LocalDate.of(2026, 10, 16),
            "ptnr_nm", "asc", 50, 0);
        verify(mapper).count("VEND", "Y", "1234567890");
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({"kind,INVALID", "useYn,X", "page,0", "size,10", "sort,acnt_no", "page,bad"})
    void invalidListParametersNeverReachMapper(String field, String value) throws Exception {
        mvc.perform(get("/api/partners").param(field, value))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper);
    }

    @Test
    void unchangedPutIgnoresBothNumberSpellingsAndNeverUpdatesTimestampOrAudit() throws Exception {
        var body = body("CUST");
        body.putObject("bizRegNo");
        body.put("biz_reg_no", "wrong").put("useYn", "N");
        mvc.perform(put("/api/partners/{id}", 7).contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.bizRegNo").value("123-45-67890"))
            .andExpect(jsonPath("$.data.useYn").value("Y"));
        verify(mapper).selectById(7);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void publicPutWritesOneChangedColumnAndReadbackRetainsIdentity() throws Exception {
        var body = body("CUST");
        body.put("ptnrNm", "변경 상호");
        mvc.perform(put("/api/partners/{id}", 7).contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.ptnrNm").value("변경 상호"));
        verify(mapper).update(7, Map.of("ptnr_nm", "변경 상호"), null);
        verify(mapper).history(7, "UPDATE", "ptnr_nm", "원래 상호", "변경 상호", null);
        mvc.perform(get("/api/partners/{id}", 7)).andExpect(jsonPath("$.data.no").value("C-007"));
    }

    @Test
    void missingPutIs404WithoutWrites() throws Exception {
        mvc.perform(put("/api/partners/{id}", 888).contentType("application/json").content(body("CUST").toString()))
            .andExpect(status().isNotFound());
        verify(mapper, never()).update(anyLong(), anyMap(), any());
    }

    @Test
    void invalidVendorPutPreservesRowAndAudit() throws Exception {
        var body = body("VEND");
        body.remove("acntNo");
        mvc.perform(put("/api/partners/{id}", 7).contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("acntNo"));
        assertThat(stored.getPtnrSeCd()).isEqualTo("CUST");
        verify(mapper, never()).update(anyLong(), anyMap(), any());
    }

    @Test
    void accountHistoryRetainsOnlyLastFourDigits() throws Exception {
        stored.setPtnrSeCd("VEND");
        stored.setBankCd("KB");
        stored.setAcntNo("1111-2222-3333");
        stored.setDpstrNm("합성예금주");
        mvc.perform(put("/api/partners/{id}", 7).contentType("application/json").content(body("VEND").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.acntNo").value("1234-5678-9012"));
        verify(mapper).history(7, "UPDATE", "acnt_no", "3333", "9012", null);
    }

    @Test
    void technicalFailureIsSanitizedNotMisclassifiedAsDuplicate() throws Exception {
        doThrow(new IllegalStateException("private jdbc SQL"))
            .when(mapper).history(anyLong(), any(), any(), any(), any(), any());
        var result = mvc.perform(post("/api/partners").contentType("application/json").content(body("CUST").toString()))
            .andExpect(status().isInternalServerError()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private", "jdbc", "SQL");
    }

    @Test
    void durableContractIsLoadedFromClasspath() throws Exception {
        String contract;
        try (var stream = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            contract = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertThat(contract).contains("operationId: getPartners", "operationId: createPartner",
            "operationId: updatePartner", "operationId: getPartner");
        mvc.perform(get("/api/partners")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray()).andExpect(jsonPath("$.data.total").value(0));
    }
}
