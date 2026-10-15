package com.sicost.operatingstandards;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 명시 PostgreSQL 환경에서만 실제 SQL·materialization·트랜잭션 롤백을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class OperatingStandardPersistenceTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "sicost"));
        registry.add("spring.datasource.password", () ->
            System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "dev-only-password"));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @SpyBean OperatingStandardMapper mapper;

    @Test
    @Transactional
    void readsNeverMutateAndCurrentQueryExcludesFutureWhileHistoryIncludesIt() throws Exception {
        String future = YearMonth.now(ZoneId.of("Asia/Seoul")).plusMonths(1).toString();
        jdbc.update("""
            INSERT INTO cmn.oprt_std (oprt_std_id, aply_bgn_ym, oprt_std_nm, num_val, unit_nm)
            VALUES ('CURRENT_TEST', '0000-01', '현재값', 10, '%'), ('CURRENT_TEST', ?, '미래값', 11, '%')
            """, future);
        var standards = standards();
        var audit = audit();
        var close = close();
        mvc.perform(get("/api/admin/operating-standards"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[?(@.oprt_std_id == 'CURRENT_TEST')].num_val").value(10));
        mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", "CURRENT_TEST"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].aply_bgn_ym").value(future))
            .andExpect(jsonPath("$.data[0].num_val").value(11));
        assertThat(standards()).isEqualTo(standards);
        assertThat(audit()).isEqualTo(audit);
        assertThat(close()).isEqualTo(close);
    }

    @Test
    @Transactional
    void savesPreserveOtherMonthsAndMetadataAndAppendAtomicParamHistory() throws Exception {
        var close = close();
        var previous = jdbc.queryForList("SELECT * FROM cmn.oprt_std WHERE oprt_std_id = 'VAT_RATE'");
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"9999-11\",\"num_val\":11}"))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        var saved = jdbc.queryForMap("""
            SELECT * FROM cmn.oprt_std
            WHERE oprt_std_id = 'VAT_RATE' AND aply_bgn_ym = '9999-11'
            """);
        assertThat(saved.get("num_val")).isEqualTo(new BigDecimal("11.0000"));
        assertThat(saved.get("created_by")).isNull();
        assertThat(jdbc.queryForMap("""
            SELECT cd_grp_id, dtl_cd, cd_chg_ty_cd, item_nm, bfr_val, aft_val, chg_user_id
            FROM cmn.cd_hist WHERE cd_grp_id = 'PARAM' AND dtl_cd = 'VAT_RATE' AND item_nm = '9999-11'
            ORDER BY cd_hist_id DESC LIMIT 1
            """)).containsEntry("cd_chg_ty_cd", "UPDATE").containsEntry("aft_val", "11")
            .containsEntry("chg_user_id", null);
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"9999-11\",\"num_val\":12}"))
            .andExpect(status().isNoContent());
        var updated = jdbc.queryForMap("""
            SELECT * FROM cmn.oprt_std
            WHERE oprt_std_id = 'VAT_RATE' AND aply_bgn_ym = '9999-11'
            """);
        assertThat(updated).containsEntry("created_at", saved.get("created_at"))
            .containsEntry("oprt_std_nm", saved.get("oprt_std_nm"))
            .containsEntry("unit_nm", saved.get("unit_nm"))
            .containsEntry("use_plc_desc", saved.get("use_plc_desc"));
        assertThat(jdbc.queryForList("""
            SELECT * FROM cmn.oprt_std
            WHERE oprt_std_id = 'VAT_RATE' AND aply_bgn_ym <> '9999-11'
            """)).containsExactlyInAnyOrderElementsOf(previous);
        assertThat(close()).isEqualTo(close);
    }

    @Test
    @Transactional
    void closedMonthRejectsWithoutChangingBusinessOrHistory() throws Exception {
        var standards = standards();
        var audit = audit();
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"2026-09\",\"num_val\":11}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));
        assertThat(standards()).isEqualTo(standards);
        assertThat(audit()).isEqualTo(audit);
    }

    @Test
    void historyFailureRollsBackRealServiceTransactionAndSanitizes500() throws Exception {
        var standards = standards();
        var audit = audit();
        doThrow(new IllegalStateException("private SQL history failure")).when(mapper)
            .appendHistory(eq("VAT_RATE"), eq("9999-12"), any(), any(), isNull());
        try {
            var response = mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                .contentType(MediaType.APPLICATION_JSON).content("{\"aply_bgn_ym\":\"9999-12\",\"num_val\":11}"))
                .andExpect(status().isInternalServerError()).andReturn();
            assertThat(response.getResponse().getContentAsString()).doesNotContain("private", "SQL");
            assertThat(standards()).isEqualTo(standards);
            assertThat(audit()).isEqualTo(audit);
        } finally {
            reset(mapper);
        }
    }

    private List<Map<String, Object>> standards() {
        return jdbc.queryForList("SELECT * FROM cmn.oprt_std ORDER BY oprt_std_id, aply_bgn_ym");
    }

    private List<Map<String, Object>> audit() {
        return jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id");
    }

    private List<Map<String, Object>> close() {
        return jdbc.queryForList("SELECT * FROM fin.mth_cls ORDER BY cls_ym");
    }
}
