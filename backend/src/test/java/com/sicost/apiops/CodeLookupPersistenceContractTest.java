package com.sicost.apiops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 PostgreSQL의 필터·정렬·월 선택·감사 불변성을 검증한다. 설정 부재는 실행 성공이 아니다. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional
class CodeLookupPersistenceContractTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "sicost"));
        registry.add("spring.datasource.password", () ->
            System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "dev-only-password"));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void disabledOpsDisappearsOnlyFromNewChoicesAndReadLeavesEveryRowAndAuditUnchanged() throws Exception {
        jdbc.update("UPDATE cmn.cd_dtl SET use_yn = 'N' WHERE cd_grp_id = 'JOB' AND dtl_cd = 'OPS'");
        jdbc.update("""
            INSERT INTO cmn.excel_map (excel_lbl_nm, trns_cd_grp_id, trns_cd, map_role_cd)
            VALUES ('선택조회 참조 보존 시험', 'JOB', 'OPS', 'MAIN')
            """);
        var before = snapshot();
        var expected = jdbc.queryForList("""
            SELECT dtl_cd
            FROM cmn.cd_dtl
            WHERE cd_grp_id = 'JOB'
                AND use_yn = 'Y'
            ORDER BY sort_seq ASC, dtl_cd ASC
            """, String.class);
        var result = mvc.perform(get("/api/codes/{group}", "JOB"))
            .andExpect(status().isOk()).andReturn();
        JsonNode data = json.readTree(result.getResponse().getContentAsByteArray()).path("data");
        var actual = new java.util.ArrayList<String>();
        for (var item : data) {
            actual.add(item.path("code").asText());
            assertThat(item.size()).isEqualTo(5);
            assertThat(item.has("attr1") && item.has("attr2") && item.has("attr3")).isTrue();
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotContain("OPS");
        assertThat(jdbc.queryForObject("""
            SELECT d.cd_nm
            FROM cmn.excel_map m
            JOIN cmn.cd_dtl d
                ON d.cd_grp_id = m.trns_cd_grp_id
                AND d.dtl_cd = m.trns_cd
            WHERE m.excel_lbl_nm = '선택조회 참조 보존 시험'
            """, String.class)).isEqualTo(jdbc.queryForObject("""
                SELECT cd_nm
                FROM cmn.cd_dtl
                WHERE cd_grp_id = 'JOB'
                    AND dtl_cd = 'OPS'
                """, String.class));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void parameterUsesLatestApplicableRequestedMonthAndReadsNeverInsertMissingRowsOrHistory() throws Exception {
        jdbc.update("""
            INSERT INTO cmn.oprt_std (oprt_std_id, aply_bgn_ym, oprt_std_nm, num_val, unit_nm)
            VALUES ('VAT_RATE', '2026-11', '부가세율', 11, '%')
            ON CONFLICT (oprt_std_id, aply_bgn_ym)
            DO UPDATE SET num_val = 11
            """);
        var before = snapshot();
        mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "2026-10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.num_val").value(10));
        mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "2026-11"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.num_val").value(11))
            .andExpect(jsonPath("$.data.aply_bgn_ym").value("2026-11"));
        mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "0000-01"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/params/{key}", "NO_SUCH_PARAMETER").param("month", "2026-11"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "2026-13"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));
        mvc.perform(get("/api/params/{key}", "VAT_RATE"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));
        assertThat(snapshot()).isEqualTo(before);
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        var snapshot = new LinkedHashMap<String, List<Map<String, Object>>>();
        snapshot.put("groups", jdbc.queryForList("SELECT * FROM cmn.cd_grp ORDER BY cd_grp_id"));
        snapshot.put("codes", jdbc.queryForList("SELECT * FROM cmn.cd_dtl ORDER BY cd_grp_id, dtl_cd"));
        snapshot.put("maps", jdbc.queryForList("SELECT * FROM cmn.excel_map ORDER BY map_id"));
        snapshot.put("params", jdbc.queryForList("SELECT * FROM cmn.oprt_std ORDER BY oprt_std_id, aply_bgn_ym"));
        snapshot.put("history", jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id"));
        return snapshot;
    }
}
