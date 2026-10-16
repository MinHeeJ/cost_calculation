package com.sicost.partnersops;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
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

/** Opt-in actual PostgreSQL mapper and transaction evidence; absence of DB settings skips before startup. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional
class PartnerActivityPersistenceTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "sicost"));
        registry.add("spring.datasource.password", () ->
            System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "dev-only-password"));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @SpyBean PartnerActivityMapper mapper;
    @MockBean(name = "partnerActivityClock") Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void fixedDate() {
        when(clock.withZone(any())).thenReturn(
            Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    // This step does not own POST /api/partners; fixture rows use existing V1 columns, not production seeds.
    private long partner(String name, String kind, String use) {
        long id = jdbc.queryForObject("SELECT nextval('mst.seq_ptnr_id')", Long.class);
        String digits = String.format("%010d", id);
        String biz = digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
        jdbc.update("""
            INSERT INTO mst.ptnr (ptnr_id, ptnr_no, ptnr_nm, ptnr_se_cd, biz_reg_no, use_yn,
                txinv_email_addr, bank_cd, acnt_no, dpstr_nm)
            VALUES (?, ?, ?, ?, ?, ?, 'test@example.com', 'KB', '123456789012', '테스트')
            """, id, "TEST-" + id, name, kind, biz, use);
        return id;
    }

    private long developer(long partner, String name, String use, String resignation) {
        return jdbc.queryForObject("""
            INSERT INTO mst.dev (mng_no, dev_nm, vndr_ptnr_id, emp_se_cd, use_yn, rsgn_dt)
            VALUES (?, ?, ?, 'OS', ?, CAST(? AS DATE))
            RETURNING dev_id
            """, Long.class, UUID.randomUUID().toString().substring(0, 20), name,
            partner == 0 ? null : partner, use, resignation);
    }

    private long project(long customer, String name, String state) {
        return jdbc.queryForObject("""
            INSERT INTO prj.prj (prj_no, prj_nm, cust_ptnr_id, cntrct_ty_cd, prj_stts_cd)
            VALUES (?, ?, ?, 'TK', ?)
            RETURNING prj_id
            """, Long.class, UUID.randomUUID().toString().substring(0, 20), name, customer, state);
    }

    private void assignment(long project, long developer, String state, String begin, String end) {
        jdbc.update("""
            INSERT INTO asgn.asgn (asgn_no, prj_id, dev_id, job_cd, asgn_stts_cd, bgn_dt, end_dt)
            VALUES (?, ?, ?, 'BE', ?, CAST(? AS DATE), CAST(? AS DATE))
            """, UUID.randomUUID().toString().substring(0, 20), project, developer, state, begin, end);
    }

    private List<Map<String, Object>> snapshot(String table, String key) {
        // Names are fixed test-owned literals, never request parameters.
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY " + key);
    }

    @Test
    void optionFiltersAndDisableEnableRoundTripPreserveIdentityAndAccount() throws Exception {
        long cust = partner("검증 가 고객", "CUST", "Y");
        long vend = partner("검증 나 협력", "VEND", "Y");
        long both = partner("검증 다 겸업", "BOTH", "Y");
        long inactive = partner("검증 라 중지", "CUST", "N");
        var before = snapshot("mst.ptnr", "ptnr_id");
        var audit = snapshot("sys.data_chg_hist", "data_chg_hist_id");
        var response = mvc.perform(get("/api/partners/options").param("kind", "CUST"))
            .andExpect(status().isOk()).andReturn();
        var data = json.readTree(response.getResponse().getContentAsByteArray()).path("data");
        var ids = new java.util.ArrayList<Long>();
        data.forEach(item -> {
            ids.add(item.path("id").asLong());
            assertThat(item.size()).isEqualTo(3);
        });
        assertThat(ids).contains(cust, both).doesNotContain(vend, inactive);
        assertThat(snapshot("mst.ptnr", "ptnr_id")).isEqualTo(before);
        assertThat(snapshot("sys.data_chg_hist", "data_chg_hist_id")).isEqualTo(audit);
        var original = jdbc.queryForMap("SELECT * FROM mst.ptnr WHERE ptnr_id = ?", cust);
        mvc.perform(post("/api/partners/disable").contentType("application/json")
            .content("{\"ids\":[" + cust + "]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.disabledCount").value(1));
        assertThat(mapper.selectOptions("CUST")).extracting(PartnerOptionRow::getId).doesNotContain(cust);
        mvc.perform(post("/api/partners/{id}/enable", cust))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.useYn").value("Y"));
        assertThat(mapper.selectOptions("CUST")).extracting(PartnerOptionRow::getId).contains(cust);
        var enabled = jdbc.queryForMap("SELECT * FROM mst.ptnr WHERE ptnr_id = ?", cust);
        for (String key : List.of("ptnr_no", "biz_reg_no", "acnt_no", "bank_cd", "dpstr_nm", "created_at")) {
            assertThat(enabled.get(key)).isEqualTo(original.get(key));
        }
        var history = mapper.selectHistory(cust);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getDataChgTyCd()).isEqualTo("UPDATE");
        assertThat(history.get(0).getClmnNm()).isEqualTo("use_yn");
        assertThat(history.get(0).getBfrVal()).isEqualTo("N");
        assertThat(history.get(0).getAftVal()).isEqualTo("Y");
        mvc.perform(post("/api/partners/{id}/enable", cust)).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("SELECT * FROM mst.ptnr WHERE ptnr_id = ?", cust)).isEqualTo(enabled);
        assertThat(mapper.selectHistory(cust)).hasSize(2);
    }

    @Test
    void assignmentMembershipDatesAndBothUnionPreserveActualRowsWithoutWrites() throws Exception {
        long b = partner("협력사 B", "BOTH", "Y");
        long customer = partner("다른 고객", "CUST", "Y");
        long developer = developer(b, "가 개발", "Y", null);
        long other = developer(b, "나 개발", "Y", null);
        long internal = developer(0, "다 자사", "Y", null);
        long p1 = project(b, "가 프로젝트", "PLAN");
        long p2 = project(customer, "나 프로젝트", "HOLD");
        long ended = project(b, "종료 프로젝트", "END");
        assignment(p1, developer, "ACTIVE", "2026-10-16", "2026-10-16");
        assignment(p1, developer, "ACTIVE", "2026-10-01", "2026-10-31");
        assignment(p2, other, "ACTIVE", "2026-10-01", "2026-10-16");
        assignment(p1, internal, "ACTIVE", "2026-10-01", "2026-10-31");
        assignment(p1, developer, "ACTIVE", "2026-10-01", "2026-10-15");
        assignment(p1, developer, "ACTIVE", "2026-10-17", "2026-10-31");
        assignment(p1, developer, "CANCELLED", "2026-10-01", "2026-10-31");
        assignment(ended, developer, "ACTIVE", "2026-10-01", "2026-10-31");
        var projects = snapshot("prj.prj", "prj_id");
        var people = snapshot("mst.dev", "dev_id");
        var assignments = snapshot("asgn.asgn", "asgn_id");
        var history = snapshot("sys.data_chg_hist", "data_chg_hist_id");
        mvc.perform(get("/api/partners/{id}/assignments", b))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4))
            .andExpect(jsonPath("$.data[0].developerName").value("가 개발"))
            .andExpect(jsonPath("$.data[0].affiliationName").value("협력사 B"))
            .andExpect(jsonPath("$.data[2].affiliationName").value("자사"))
            .andExpect(jsonPath("$.data[3].projectName").value("나 프로젝트"));
        assertThat(mapper.countOngoingProjects(b, java.time.LocalDate.of(2026, 10, 16))).isEqualTo(2);
        assertThat(snapshot("prj.prj", "prj_id")).isEqualTo(projects);
        assertThat(snapshot("mst.dev", "dev_id")).isEqualTo(people);
        assertThat(snapshot("asgn.asgn", "asgn_id")).isEqualTo(assignments);
        assertThat(snapshot("sys.data_chg_hist", "data_chg_hist_id")).isEqualTo(history);
    }

    @Test
    void disableCountsAffiliationRegardlessOfKindAndExcludesTodayResignation() throws Exception {
        long blocked = partner("소속 검사", "CUST", "Y");
        developer(blocked, "유효1", "Y", null);
        developer(blocked, "유효2", "Y", "2026-10-17");
        developer(blocked, "오늘 퇴사", "Y", "2026-10-16");
        developer(blocked, "미사용", "N", null);
        var rows = snapshot("mst.ptnr", "ptnr_id");
        var history = snapshot("sys.data_chg_hist", "data_chg_hist_id");
        mvc.perform(post("/api/partners/disable").contentType("application/json")
            .content("{\"ids\":[" + blocked + "]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.excluded[0].affiliatedDeveloperCount").value(2));
        assertThat(snapshot("mst.ptnr", "ptnr_id")).isEqualTo(rows);
        assertThat(snapshot("sys.data_chg_hist", "data_chg_hist_id")).isEqualTo(history);
    }

    @Test
    void historyIsIsolatedSortedAndNeverWritesOrExpandsMaskedValues() throws Exception {
        long id = partner("이력 대상", "VEND", "Y");
        long other = partner("다른 이력", "CUST", "Y");
        jdbc.update("""
            INSERT INTO sys.data_chg_hist (tbl_nm, obj_rec_id, data_chg_ty_cd, chg_at)
            VALUES ('mst.ptnr', ?, 'INSERT', '2026-10-01 10:00:00')
            """, Long.toString(id));
        jdbc.update("""
            INSERT INTO sys.data_chg_hist (tbl_nm, obj_rec_id, data_chg_ty_cd, clmn_nm, bfr_val, aft_val, chg_at)
            VALUES ('mst.ptnr', ?, 'UPDATE', 'acnt_no', '1234', '5678', '2026-10-02 10:00:00')
            """, Long.toString(id));
        jdbc.update("""
            INSERT INTO sys.data_chg_hist (tbl_nm, obj_rec_id, data_chg_ty_cd)
            VALUES ('mst.ptnr', ?, 'INSERT')
            """, Long.toString(other));
        var history = snapshot("sys.data_chg_hist", "data_chg_hist_id");
        mvc.perform(get("/api/partners/{id}/history", id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].bfrVal").value("1234"))
            .andExpect(jsonPath("$.data[0].aftVal").value("5678"))
            .andExpect(jsonPath("$.data[1].dataChgTyCd").value("INSERT"));
        assertThat(snapshot("sys.data_chg_hist", "data_chg_hist_id")).isEqualTo(history);
    }

    @Test
    void secondDisableAuditFailureRollsBackAllEligibleRowsThroughRealProxy() throws Exception {
        TestTransaction.end();
        long a = partner("원자성 가", "CUST", "Y");
        long b = partner("원자성 나", "CUST", "Y");
        var before = snapshot("mst.ptnr", "ptnr_id");
        var history = snapshot("sys.data_chg_hist", "data_chg_hist_id");
        doThrow(new IllegalStateException("private audit SQL"))
            .when(mapper).insertHistory(eq(b), eq("DISABLE"), isNull(), isNull(), isNull(), isNull());
        try {
            mvc.perform(post("/api/partners/disable").contentType("application/json")
                .content("{\"ids\":[" + a + "," + b + "]}"))
                .andExpect(status().isInternalServerError());
            assertThat(snapshot("mst.ptnr", "ptnr_id")).isEqualTo(before);
            assertThat(snapshot("sys.data_chg_hist", "data_chg_hist_id")).isEqualTo(history);
        } finally {
            reset(mapper);
            jdbc.update("DELETE FROM mst.ptnr WHERE ptnr_id IN (?, ?)", a, b);
            TestTransaction.start();
        }
    }

    @Test
    void enableAuditFailureRollsBackStateAndTimestamp() throws Exception {
        TestTransaction.end();
        long id = partner("재사용 원자성", "VEND", "N");
        var before = snapshot("mst.ptnr", "ptnr_id");
        var history = snapshot("sys.data_chg_hist", "data_chg_hist_id");
        doThrow(new IllegalStateException("private audit SQL"))
            .when(mapper).insertHistory(eq(id), eq("UPDATE"), eq("use_yn"), eq("N"), eq("Y"), isNull());
        try {
            mvc.perform(post("/api/partners/{id}/enable", id)).andExpect(status().isInternalServerError());
            assertThat(snapshot("mst.ptnr", "ptnr_id")).isEqualTo(before);
            assertThat(snapshot("sys.data_chg_hist", "data_chg_hist_id")).isEqualTo(history);
        } finally {
            reset(mapper);
            jdbc.update("DELETE FROM mst.ptnr WHERE ptnr_id = ?", id);
            TestTransaction.start();
        }
    }
}
