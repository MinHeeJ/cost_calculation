package com.sicost.excelmaps;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL/Flyway evidence, opt-in before context startup like SharedPersistenceContractTest. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ExcelMapPersistenceContractTest {
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

    private String body(String code) throws Exception {
        return json.writeValueAsString(Map.of("excel_lbl_nm", "계약검증표기", "trns_cd_grp_id", "JOB",
            "trns_cd", code, "map_role_cd", "MAIN"));
    }

    private List<Map<String, Object>> maps() {
        return jdbc.queryForList("SELECT * FROM cmn.excel_map ORDER BY map_id");
    }

    private List<Map<String, Object>> histories() {
        return jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id");
    }

    @Test
    @Transactional
    void publicCreateDuplicateDifferentTargetAndJoinedListPreserveUnrelatedRows() throws Exception {
        var codes = jdbc.queryForList("SELECT * FROM cmn.cd_dtl ORDER BY cd_grp_id, dtl_cd");
        var months = jdbc.queryForList("SELECT * FROM fin.mth_cls ORDER BY cls_ym");
        mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(body("BE")))
            .andExpect(status().isNoContent()).andExpect(content().bytes(new byte[0]));
        long id = jdbc.queryForObject(
            "SELECT map_id FROM cmn.excel_map WHERE excel_lbl_nm = ? AND trns_cd = ?",
            Long.class, "계약검증표기", "BE");
        var created = jdbc.queryForMap("SELECT * FROM cmn.excel_map WHERE map_id = ?", id);
        assertThat(created).containsEntry("use_yn", "Y").containsEntry("map_role_cd", "MAIN")
            .containsEntry("created_by", null);
        var history = jdbc.queryForMap("SELECT * FROM cmn.cd_hist WHERE item_nm = ?", "excel_map." + id);
        assertThat(history).containsEntry("cd_grp_id", "JOB").containsEntry("dtl_cd", "BE")
            .containsEntry("cd_chg_ty_cd", "ADD").containsEntry("chg_user_id", null);
        var before = maps();
        var audit = histories();
        mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(body("BE")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        assertThat(maps()).isEqualTo(before);
        assertThat(histories()).isEqualTo(audit);
        mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(body("OPS")))
            .andExpect(status().isNoContent());
        jdbc.update("UPDATE cmn.excel_map SET use_yn = 'N', map_role_cd = 'SUB' WHERE map_id = ?", id);
        jdbc.update("UPDATE cmn.cd_dtl SET use_yn = 'N' WHERE cd_grp_id = 'JOB' AND dtl_cd = 'BE'");
        before = maps();
        audit = histories();
        var response = mvc.perform(get("/api/admin/excel-maps")).andExpect(status().isOk()).andReturn();
        var data = json.readTree(response.getResponse().getContentAsByteArray()).path("data");
        var expected = jdbc.queryForList("""
            SELECT e.map_id, e.excel_lbl_nm, g.cd_grp_nm, d.cd_nm
            FROM cmn.excel_map e
            JOIN cmn.cd_grp g ON g.cd_grp_id = e.trns_cd_grp_id
            JOIN cmn.cd_dtl d ON d.cd_grp_id = e.trns_cd_grp_id AND d.dtl_cd = e.trns_cd
            ORDER BY e.excel_lbl_nm, e.map_id
            """);
        assertThat(data.size()).isEqualTo(expected.size());
        boolean found = false;
        for (int i = 0; i < expected.size(); i++) {
            assertThat(data.get(i).path("map_id").asLong()).isEqualTo(expected.get(i).get("map_id"));
            assertThat(data.get(i).path("cd_grp_nm").asText()).isEqualTo(expected.get(i).get("cd_grp_nm"));
            assertThat(data.get(i).path("cd_nm").asText()).isEqualTo(expected.get(i).get("cd_nm"));
            if (data.get(i).path("map_id").asLong() == id) {
                found = true;
                assertThat(data.get(i).path("use_yn").asText()).isEqualTo("N");
                assertThat(data.get(i).path("map_role_cd").asText()).isEqualTo("SUB");
            }
        }
        assertThat(found).isTrue();
        assertThat(maps()).isEqualTo(before);
        assertThat(histories()).isEqualTo(audit);
        jdbc.update("UPDATE cmn.cd_dtl SET use_yn = 'Y' WHERE cd_grp_id = 'JOB' AND dtl_cd = 'BE'");
        // Trigger-owned updated_at changes in this isolated test are not domain conversion.
        assertThat(jdbc.queryForList("SELECT * FROM fin.mth_cls ORDER BY cls_ym")).isEqualTo(months);
        assertThat(jdbc.queryForList("SELECT * FROM cmn.cd_dtl ORDER BY cd_grp_id, dtl_cd")).hasSize(codes.size());
    }

    @Test
    @Transactional
    void patchHasFieldHistoriesMemoPresenceStateTransitionsAndNoOp() throws Exception {
        mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(body("BE")))
            .andExpect(status().isNoContent());
        long id = jdbc.queryForObject("SELECT map_id FROM cmn.excel_map WHERE excel_lbl_nm = ?",
            Long.class, "계약검증표기");
        var original = jdbc.queryForMap("SELECT * FROM cmn.excel_map WHERE map_id = ?", id);
        var codes = jdbc.queryForList("SELECT * FROM cmn.cd_dtl ORDER BY cd_grp_id, dtl_cd");
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", id).contentType("application/json")
            .content("{\"map_role_cd\":\"SUB\",\"rmrk_desc\":\"수정\",\"use_yn\":\"N\"}"))
            .andExpect(status().isNoContent()).andExpect(content().bytes(new byte[0]));
        var updated = jdbc.queryForMap("SELECT * FROM cmn.excel_map WHERE map_id = ?", id);
        for (String field : List.of(
            "map_id", "excel_lbl_nm", "trns_cd_grp_id", "trns_cd", "created_at", "created_by"
        )) {
            assertThat(updated.get(field)).isEqualTo(original.get(field));
        }
        var history = jdbc.queryForList("SELECT * FROM cmn.cd_hist WHERE item_nm LIKE ? ORDER BY cd_hist_id",
            "excel_map." + id + ".%");
        assertThat(history).hasSize(3);
        assertThat(history.get(0)).containsEntry("bfr_val", "MAIN").containsEntry("aft_val", "SUB");
        assertThat(history.get(1)).containsEntry("bfr_val", null).containsEntry("aft_val", "수정");
        assertThat(history.get(2)).containsEntry("cd_chg_ty_cd", "DISABLE").containsEntry("bfr_val", "Y")
            .containsEntry("aft_val", "N").containsEntry("chg_user_id", null);
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", id).contentType("application/json")
            .content("{\"use_yn\":\"Y\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT rmrk_desc FROM cmn.excel_map WHERE map_id = ?", String.class, id))
            .isEqualTo("수정");
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", id).contentType("application/json")
            .content("{\"rmrk_desc\":null}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT rmrk_desc FROM cmn.excel_map WHERE map_id = ?", String.class, id))
            .isNull();
        var audit = histories();
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", id).contentType("application/json")
            .content("{\"rmrk_desc\":null}")).andExpect(status().isNoContent());
        assertThat(histories()).isEqualTo(audit);
        assertThat(jdbc.queryForList("SELECT * FROM cmn.cd_dtl ORDER BY cd_grp_id, dtl_cd")).isEqualTo(codes);
    }

    private void rejectMappingHistory() {
        jdbc.execute("""
            ALTER TABLE cmn.cd_hist ADD CONSTRAINT excel_map_contract_reject
            CHECK (item_nm NOT LIKE 'excel_map.%') NOT VALID
            """);
    }

    @Test
    void failedCreateHistoryRollsBackRealInsert() throws Exception {
        var before = maps();
        var audit = histories();
        rejectMappingHistory();
        try {
            mvc.perform(post("/api/admin/excel-maps").contentType("application/json").content(body("BE")))
                .andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("ALTER TABLE cmn.cd_hist DROP CONSTRAINT excel_map_contract_reject");
        }
        assertThat(maps()).isEqualTo(before);
        assertThat(histories()).isEqualTo(audit);
    }

    @Test
    void failedPatchHistoryRollsBackRealUpdate() throws Exception {
        var before = maps();
        var audit = histories();
        long id = jdbc.queryForObject("SELECT MIN(map_id) FROM cmn.excel_map", Long.class);
        rejectMappingHistory();
        try {
            mvc.perform(patch("/api/admin/excel-maps/{map_id}", id).contentType("application/json")
                .content("{\"rmrk_desc\":\"이력실패검증\"}"))
                .andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("ALTER TABLE cmn.cd_hist DROP CONSTRAINT excel_map_contract_reject");
        }
        assertThat(maps()).isEqualTo(before);
        assertThat(histories()).isEqualTo(audit);
    }
}
