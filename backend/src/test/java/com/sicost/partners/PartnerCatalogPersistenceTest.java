package com.sicost.partners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in real PostgreSQL tests; absent connection settings skip before Spring context creation. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional
class PartnerCatalogPersistenceTest {
    @org.springframework.boot.test.mock.mockito.MockBean(name = "partnerCatalogClock")
    Clock clock;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "sicost"));
        registry.add("spring.datasource.password", () ->
            System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "dev-only-password"));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @SpyBean PartnerCatalogMapper mapper;
    private final ObjectMapper json = new ObjectMapper();
    private int sequence;

    @BeforeEach
    void bank() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-15T16:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        jdbc.update("""
            INSERT INTO cmn.cd_dtl (cd_grp_id, dtl_cd, cd_nm, sort_seq)
            VALUES ('BANK', 'KB', '국민은행', 1)
            ON CONFLICT (cd_grp_id, dtl_cd) DO NOTHING
            """);
    }

    private ObjectNode body(String kind) {
        String number = String.format(java.util.Locale.ROOT, "98%08d", ++sequence);
        ObjectNode body = json.createObjectNode().put("ptnrSeCd", kind)
            .put("ptnrNm", "합성거래처" + sequence).put("bizRegNo", number);
        if (!kind.equals("VEND")) {
            body.put("txinvEmailAddr", "tax@example.test");
        }
        if (!kind.equals("CUST")) {
            body.put("bankCd", "KB").put("acntNo", "1234-5678-9012").put("dpstrNm", "합성예금주");
        }
        return body;
    }

    private JsonNode create(ObjectNode body) throws Exception {
        var response = mvc.perform(post("/api/partners").contentType("application/json").content(body.toString()))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsByteArray()).path("data");
    }

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList("SELECT * FROM mst.ptnr ORDER BY ptnr_id");
    }

    private List<Map<String, Object>> histories() {
        return jdbc.queryForList("SELECT * FROM sys.data_chg_hist ORDER BY data_chg_hist_id");
    }

    @Test
    void listDefaultFiltersNameOrderSafeAccountAndTotalAreReadOnly() throws Exception {
        var a = body("CUST").put("ptnrNm", "가합성");
        var b = body("VEND").put("ptnrNm", "나합성");
        var c = body("BOTH").put("ptnrNm", "다합성");
        create(c);
        create(a);
        create(b);
        long disabled = create(body("CUST")).path("id").asLong();
        jdbc.update("UPDATE mst.ptnr SET use_yn = 'N' WHERE ptnr_id = ?", disabled);
        var before = rows();
        var history = histories();
        mvc.perform(get("/api/partners"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.items[0].ptnrNm").value("가합성"));
        var response = mvc.perform(get("/api/partners").param("kind", "VEND"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andReturn();
        String wire = response.getResponse().getContentAsString();
        assertThat(wire).contains("9012").doesNotContain("1234-5678-9012", "\"bankCd\"", "\"dpstrNm\"");
        mvc.perform(get("/api/partners").param("q", a.path("bizRegNo").asText()))
            .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/partners").param("q", "없는상호"))
            .andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.items").isEmpty());
        assertThat(rows()).isEqualTo(before);
        assertThat(histories()).isEqualTo(history);
    }

    @Test
    void fiftyOnePartnersAreSortedBeforePageCutAndCountRetainsAllMatches() throws Exception {
        for (int i = 0; i < 51; i++) {
            create(body("CUST"));
        }
        mvc.perform(get("/api/partners"))
            .andExpect(jsonPath("$.data.items.length()").value(50)).andExpect(jsonPath("$.data.total").value(51));
        mvc.perform(get("/api/partners").param("page", "2"))
            .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.total").value(51));
    }

    @Test
    void insertAuditAndNoOpPutPreserveIdentityAndTimestamp() throws Exception {
        ObjectNode body = body("CUST");
        long id = create(body).path("id").asLong();
        assertThat(jdbc.queryForList("""
            SELECT data_chg_ty_cd, clmn_nm, bfr_val, aft_val, chg_user_id
            FROM sys.data_chg_hist
            WHERE tbl_nm = 'mst.ptnr' AND obj_rec_id = ?
            """, String.valueOf(id))).singleElement().satisfies(row ->
                assertThat(row).containsEntry("data_chg_ty_cd", "INSERT").containsEntry("clmn_nm", null)
                    .containsEntry("bfr_val", null).containsEntry("aft_val", null).containsEntry("chg_user_id", null));
        var before = rows();
        var history = histories();
        body.put("bizRegNo", "bad").put("biz_reg_no", "bad");
        mvc.perform(put("/api/partners/{id}", id).contentType("application/json").content(body.toString()))
            .andExpect(status().isOk());
        assertThat(rows()).isEqualTo(before);
        assertThat(histories()).isEqualTo(history);
        body.put("ptnrNm", "실제변경");
        mvc.perform(put("/api/partners/{id}", id).contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.ptnrNm").value("실제변경"));
        assertThat(jdbc.queryForList("""
            SELECT clmn_nm, bfr_val, aft_val
            FROM sys.data_chg_hist
            WHERE obj_rec_id = ? AND data_chg_ty_cd = 'UPDATE'
            """, String.valueOf(id))).singleElement().satisfies(row ->
                assertThat(row).containsEntry("clmn_nm", "ptnr_nm").containsEntry("aft_val", "실제변경"));
    }

    @Test
    void currentCountsDeduplicateBothPathsAndHonorInclusiveSeoulDate() throws Exception {
        long id = create(body("BOTH")).path("id").asLong();
        long p1 = project(id, "PLAN");
        long p2 = project(id, "RUN");
        long ended = project(id, "END");
        for (int i = 0; i < 5; i++) {
            long dev = developer(id);
            assignment(p1, dev, "ACTIVE", "2026-10-16", "2026-10-16");
            if (i == 0) {
                assignment(p2, dev, "ACTIVE", "2026-10-01", "2026-10-31");
            }
        }
        assignment(p1, developer(id), "ACTIVE", "2026-10-01", "2026-10-15");
        assignment(ended, developer(id), "ACTIVE", "2026-10-01", "2026-10-31");
        assignment(p1, developer(id), "CANCELLED", "2026-10-01", "2026-10-31");
        mvc.perform(get("/api/partners").param("kind", "BOTH"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].ongoingProjectCount").value(2))
            .andExpect(jsonPath("$.data.items[0].assignedDeveloperCount").value(5));
    }

    @Test
    void kindChangesKeepAllExistingConnectionsAndMaskStoredAccountHistory() throws Exception {
        var body = body("BOTH");
        long id = create(body).path("id").asLong();
        long project = project(id, "RUN");
        long dev = developer(id);
        assignment(project, dev, "ACTIVE", "2026-10-01", "2026-10-31");
        var projects = jdbc.queryForList("SELECT * FROM prj.prj ORDER BY prj_id");
        var developers = jdbc.queryForList("SELECT * FROM mst.dev ORDER BY dev_id");
        var assignments = jdbc.queryForList("SELECT * FROM asgn.asgn ORDER BY asgn_id");
        body.put("acntNo", "9876-5432-1098");
        mvc.perform(put("/api/partners/{id}", id).contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.acntNo").value("9876-5432-1098"));
        assertThat(jdbc.queryForMap("""
            SELECT bfr_val, aft_val FROM sys.data_chg_hist
            WHERE obj_rec_id = ? AND clmn_nm = 'acnt_no'
            """, String.valueOf(id))).containsEntry("bfr_val", "9012").containsEntry("aft_val", "1098");
        for (String kind : List.of("CUST", "VEND", "BOTH")) {
            body.put("ptnrSeCd", kind);
            mvc.perform(put("/api/partners/{id}", id).contentType("application/json").content(body.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ptnrSeCd").value(kind));
        }
        assertThat(jdbc.queryForList("SELECT * FROM prj.prj ORDER BY prj_id")).isEqualTo(projects);
        assertThat(jdbc.queryForList("SELECT * FROM mst.dev ORDER BY dev_id")).isEqualTo(developers);
        assertThat(jdbc.queryForList("SELECT * FROM asgn.asgn ORDER BY asgn_id")).isEqualTo(assignments);
    }

    @Test
    void realServiceTransactionRollsBackPartnerWhenInsertAuditFails() throws Exception {
        TestTransaction.end();
        var before = rows();
        var history = histories();
        doThrow(new IllegalStateException("private SQL"))
            .when(mapper).history(anyLong(), anyString(), any(), any(), any(), isNull());
        try {
            mvc.perform(post("/api/partners").contentType("application/json").content(body("CUST").toString()))
                .andExpect(status().isInternalServerError());
            assertThat(rows()).isEqualTo(before);
            assertThat(histories()).isEqualTo(history);
        } finally {
            reset(mapper);
            TestTransaction.start();
        }
    }

    private long project(long partner, String state) {
        return jdbc.queryForObject("""
            INSERT INTO prj.prj (prj_no, prj_nm, cust_ptnr_id, cntrct_ty_cd, prj_stts_cd)
            VALUES (?, '합성프로젝트', ?, 'TK', ?) RETURNING prj_id
            """, Long.class, "TEST-P-" + (++sequence), partner, state);
    }

    private long developer(long partner) {
        return jdbc.queryForObject("""
            INSERT INTO mst.dev (mng_no, dev_nm, vndr_ptnr_id, emp_se_cd)
            VALUES (?, '합성개발자', ?, 'OS') RETURNING dev_id
            """, Long.class, "TEST-D-" + (++sequence), partner);
    }

    private void assignment(long project, long developer, String state, String begin, String end) {
        jdbc.update("""
            INSERT INTO asgn.asgn (asgn_no, prj_id, dev_id, job_cd, bgn_dt, end_dt, asgn_stts_cd)
            VALUES (?, ?, ?, 'BE', CAST(? AS DATE), CAST(? AS DATE), ?)
            """, "TEST-A-" + (++sequence), project, developer, begin, end, state);
    }
}
