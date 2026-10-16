package com.sicost.operatingstandards;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional
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

    private List<Map<String, Object>> standards() {
        return jdbc.queryForList("SELECT * FROM cmn.oprt_std ORDER BY oprt_std_id, aply_bgn_ym");
    }

    private List<Map<String, Object>> audit() {
        return jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id");
    }

    private List<Map<String, Object>> closed() {
        return jdbc.queryForList("SELECT * FROM fin.mth_cls ORDER BY cls_ym");
    }

    @Test
    void currentReadExcludesFutureAndHistoryIncludesItWithoutAnyMutation() throws Exception {
        String future = YearMonth.now(ZoneId.of("Asia/Seoul")).plusMonths(2).toString();
        jdbc.update("""
            INSERT INTO cmn.oprt_std (oprt_std_id, aply_bgn_ym, oprt_std_nm, num_val, unit_nm)
            VALUES ('VAT_RATE', ?, '부가세율', 11, '%')
            """, future);
        var before = standards();
        var history = audit();
        var months = closed();
        var result = mvc.perform(get("/api/admin/operating-standards"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.last_closed_ym").value("2026-09"))
            .andReturn();
        var items = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray())
            .path("data").path("items");
        boolean found = false;
        for (var row : items) {
            if (row.path("oprt_std_id").asText().equals("VAT_RATE")) {
                found = true;
                assertThat(row.path("num_val").decimalValue()).isEqualByComparingTo("10");
                assertThat(row.path("aply_bgn_ym").asText()).isNotEqualTo(future);
            }
        }
        assertThat(found).isTrue();
        mvc.perform(get("/api/admin/operating-standards/{oprt_std_id}/history", "VAT_RATE"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].aply_bgn_ym").value(future))
            .andExpect(jsonPath("$.data[1].num_val").value(10));
        assertThat(standards()).isEqualTo(before);
        assertThat(audit()).isEqualTo(history);
        assertThat(closed()).isEqualTo(months);
    }

    @Test
    void newMonthAndSameMonthUpsertKeepMetadataPreviousValuesAndAtomicParamHistory() throws Exception {
        var months = closed();
        var original = jdbc.queryForMap("SELECT * FROM cmn.oprt_std WHERE oprt_std_id = 'VAT_RATE'");
        for (int value : new int[]{11, 12}) {
            mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                    .contentType("application/json")
                    .content("{" + "\"aply_bgn_ym\":\"2026-11\",\"num_val\":" + value + "}"))
                .andExpect(status().isNoContent()).andExpect(content().bytes(new byte[0]))
                .andExpect(header().exists("X-Request-Id"));
        }
        var saved = jdbc.queryForMap("""
            SELECT * FROM cmn.oprt_std
            WHERE oprt_std_id = 'VAT_RATE'
                AND aply_bgn_ym = '2026-11'
            """);
        assertThat(saved.get("num_val")).isEqualTo(new BigDecimal("12.0000"));
        for (String field : List.of("oprt_std_nm", "unit_nm", "use_plc_desc", "oprt_std_desc")) {
            assertThat(saved.get(field)).isEqualTo(original.get(field));
        }
        assertThat(saved.get("created_by")).isNull();
        assertThat(jdbc.queryForMap("""
            SELECT * FROM cmn.oprt_std
            WHERE oprt_std_id = 'VAT_RATE'
                AND aply_bgn_ym = '2000-01'
            """)).isEqualTo(original);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM cmn.oprt_std
            WHERE oprt_std_id = 'VAT_RATE'
            """, Integer.class)).isEqualTo(2);
        var changes = jdbc.queryForList("""
            SELECT * FROM cmn.cd_hist
            WHERE cd_grp_id = 'PARAM'
                AND dtl_cd = 'VAT_RATE'
            ORDER BY cd_hist_id
            """);
        assertThat(changes).hasSize(2);
        assertThat(changes.get(1)).containsEntry("cd_chg_ty_cd", "UPDATE")
            .containsEntry("item_nm", "2026-11").containsEntry("bfr_val", "11")
            .containsEntry("aft_val", "12").containsEntry("chg_user_id", null);
        assertThat(closed()).isEqualTo(months);
    }

    @Test
    void closedMonthRefusalLeavesBusinessAndAuditUnchanged() throws Exception {
        var before = standards();
        var history = audit();
        var months = closed();
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                .contentType("application/json").content("""
                    {"aply_bgn_ym":"2026-09","num_val":11}
                    """))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));
        assertThat(standards()).isEqualTo(before);
        assertThat(audit()).isEqualTo(history);
        assertThat(closed()).isEqualTo(months);
    }

    @Test
    void historyInsertFailureRollsBackTheRealServiceTransaction() throws Exception {
        // End the surrounding test transaction so the HTTP service owns its actual rollback boundary.
        TestTransaction.end();
        var before = standards();
        var history = audit();
        doThrow(new IllegalStateException("private SQL failure"))
            .when(mapper).insertHistory(anyString(), anyString(), any(), any(), isNull());
        try {
            var result = mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                    .contentType("application/json").content("""
                        {"aply_bgn_ym":"2026-11","num_val":11}
                        """))
                .andExpect(status().isInternalServerError()).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("private", "SQL");
            assertThat(standards()).isEqualTo(before);
            assertThat(audit()).isEqualTo(history);
        } finally {
            reset(mapper);
            TestTransaction.start();
        }
    }
}
