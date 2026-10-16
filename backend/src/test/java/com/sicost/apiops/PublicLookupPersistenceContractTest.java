package com.sicost.apiops;

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

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional
class PublicLookupPersistenceContractTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "sicost"));
        registry.add("spring.datasource.password", () ->
            System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "dev-only-password"));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void activeChoicesAreSortedAndInactiveCodeNameAndStoredReferenceAreUnchanged() throws Exception {
        jdbc.update("UPDATE cmn.cd_dtl SET use_yn = 'N' WHERE cd_grp_id = 'JOB' AND dtl_cd = 'OPS'");
        jdbc.update("""
            INSERT INTO cmn.excel_map (excel_lbl_nm, trns_cd_grp_id, trns_cd, map_role_cd)
            VALUES ('공개조회 보존 검증', 'JOB', 'OPS', 'MAIN')
            """);
        var before = snapshot();
        var result = mvc.perform(get("/api/codes/{group}", "JOB"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn();
        var data = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray()).path("data");
        var expected = jdbc.queryForList("""
            SELECT dtl_cd
            FROM cmn.cd_dtl
            WHERE cd_grp_id = 'JOB'
                AND use_yn = 'Y'
            ORDER BY sort_seq ASC, dtl_cd ASC
            """, String.class);
        var codes = new java.util.ArrayList<String>();
        for (var row : data) {
            codes.add(row.path("code").asText());
            assertThat(row.size()).isEqualTo(5);
            assertThat(row.has("attr1")).isTrue();
            assertThat(row.has("attr2")).isTrue();
            assertThat(row.has("attr3")).isTrue();
        }
        assertThat(codes).containsExactlyElementsOf(expected).doesNotContain("OPS");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void requestedMonthSelectsLatestApplicableValueIncludingFutureRowsWithoutMutation() throws Exception {
        jdbc.update("""
            INSERT INTO cmn.oprt_std
                (oprt_std_id, aply_bgn_ym, oprt_std_nm, num_val, txt_val, unit_nm, use_plc_desc)
            SELECT oprt_std_id, '2026-11', oprt_std_nm, 11, NULL, unit_nm, use_plc_desc
            FROM cmn.oprt_std
            WHERE oprt_std_id = 'VAT_RATE'
                AND aply_bgn_ym = '2000-01'
            """);
        var before = snapshot();
        mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "2026-10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.num_val").value(10))
            .andExpect(jsonPath("$.data.aply_bgn_ym").value("2000-01"));
        mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "2026-11"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.num_val").value(11))
            .andExpect(jsonPath("$.data.aply_bgn_ym").value("2026-11"));
        mvc.perform(get("/api/params/{key}", "VAT_RATE").param("month", "1999-12"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("404"));
        mvc.perform(get("/api/params/{key}", "UNKNOWN").param("month", "2026-10"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("404"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void allInactiveExistingGroupIsEmptyButUnknownGroupIsNotFound() throws Exception {
        jdbc.update("UPDATE cmn.cd_dtl SET use_yn = 'N' WHERE cd_grp_id = 'JOB'");
        var before = snapshot();
        mvc.perform(get("/api/codes/{group}", "JOB"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/codes/{group}", "UNKNOWN"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("404"));
        assertThat(snapshot()).isEqualTo(before);
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        result.put("groups", jdbc.queryForList("SELECT * FROM cmn.cd_grp ORDER BY cd_grp_id"));
        result.put("codes", jdbc.queryForList("SELECT * FROM cmn.cd_dtl ORDER BY cd_grp_id, dtl_cd"));
        result.put("history", jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id"));
        result.put("maps", jdbc.queryForList("SELECT * FROM cmn.excel_map ORDER BY map_id"));
        result.put("params", jdbc.queryForList("SELECT * FROM cmn.oprt_std ORDER BY oprt_std_id, aply_bgn_ym"));
        return result;
    }
}
