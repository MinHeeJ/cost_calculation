package com.sicost.codegroups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 명시적으로 제공된 격리 PostgreSQL에서만 SQL·감사·실제 rollback을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CodeGroupPersistenceTest {
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

    private List<Map<String, Object>> snapshot(String table, String key) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY " + key);
    }

    @Test
    @Transactional
    void publicSeedReadsAreSortedKeepDisabledAndDoNotChangeAnyAuditFields() throws Exception {
        jdbc.update("UPDATE cmn.cd_dtl SET use_yn='N' WHERE cd_grp_id='JOB' AND dtl_cd='OPS'");
        var groups = snapshot("cmn.cd_grp", "cd_grp_id");
        var codes = snapshot("cmn.cd_dtl", "cd_grp_id, dtl_cd");
        var history = snapshot("cmn.cd_hist", "cd_hist_id");
        JsonNode data = read("/api/admin/code-groups");
        int previous = Integer.MIN_VALUE;
        for (JsonNode row : data) {
            assertThat(row.path("sort_seq").intValue()).isGreaterThanOrEqualTo(previous);
            previous = row.path("sort_seq").intValue();
            if (row.path("cd_grp_id").asText().equals("JOB")) {
                assertThat(row.path("code_count").intValue()).isEqualTo(12);
                assertThat(row.path("attr1_nm").asText()).isEqualTo("기본 단가(원)");
            }
        }
        JsonNode rows = read("/api/admin/code-groups/JOB/codes");
        assertThat(rows.size()).isEqualTo(12);
        previous = Integer.MIN_VALUE;
        for (JsonNode row : rows) {
            assertThat(row.path("sort_seq").intValue()).isGreaterThanOrEqualTo(previous);
            previous = row.path("sort_seq").intValue();
            if (row.path("dtl_cd").asText().equals("OPS")) assertThat(row.path("use_yn").asText()).isEqualTo("N");
        }
        assertThat(snapshot("cmn.cd_grp", "cd_grp_id")).isEqualTo(groups);
        assertThat(snapshot("cmn.cd_dtl", "cd_grp_id, dtl_cd")).isEqualTo(codes);
        assertThat(snapshot("cmn.cd_hist", "cd_hist_id")).isEqualTo(history);
    }

    @Test
    @Transactional
    void createGroupGetsLastSequenceBizAndOneNullActorHistoryWithoutCopyingCodes() throws Exception {
        int max = jdbc.queryForObject("SELECT MAX(sort_seq) FROM cmn.cd_grp", Integer.class);
        mvc.perform(post("/api/admin/code-groups").contentType("application/json")
            .content("{\"cd_grp_id\":\"TEST_GROUP\",\"cd_grp_nm\":\"테스트 그룹\"}"))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        var group = jdbc.queryForMap("SELECT * FROM cmn.cd_grp WHERE cd_grp_id='TEST_GROUP'");
        assertThat(group.get("sort_seq")).isEqualTo(max + 1);
        assertThat(group.get("cd_grp_se_cd")).isEqualTo("BIZ");
        assertThat(group.get("created_by")).isNull();
        var history = jdbc.queryForMap("SELECT * FROM cmn.cd_hist WHERE cd_grp_id='TEST_GROUP'");
        assertThat(history.get("dtl_cd")).isNull();
        assertThat(history.get("chg_user_id")).isNull();
        assertThat(history.get("cd_chg_ty_cd")).isEqualTo("ADD");
        assertThat(read("/api/admin/code-groups/TEST_GROUP/codes").size()).isZero();
    }

    @Test
    @Transactional
    void fullSaveMaterializesCodeAndAttributeHistoryAndLastWriteWins() throws Exception {
        ArrayNode rows = (ArrayNode) read("/api/admin/code-groups/POSITION/codes");
        for (JsonNode row : rows) {
            ((ObjectNode) row).remove("cd_grp_id");
            if (row.path("dtl_cd").asText().equals("TL")) ((ObjectNode) row).put("attr1_val", "250000");
        }
        putRows("POSITION", rows, 204);
        var history = jdbc.queryForMap("""
            SELECT *
            FROM cmn.cd_hist
            WHERE cd_grp_id='POSITION' AND dtl_cd='TL' AND item_nm='attr1_val'
            ORDER BY cd_hist_id DESC
            LIMIT 1
            """);
        assertThat(history.get("bfr_val")).isEqualTo("200000");
        assertThat(history.get("aft_val")).isEqualTo("250000");
        assertThat(history.get("chg_user_id")).isNull();
        assertThat(history.get("cd_chg_ty_cd")).isEqualTo("UPDATE");
        for (JsonNode row : rows) {
            if (row.path("dtl_cd").asText().equals("TL")) ((ObjectNode) row).put("cd_nm", "최종 이름");
        }
        putRows("POSITION", rows, 204);
        assertThat(jdbc.queryForObject(
            "SELECT cd_nm FROM cmn.cd_dtl WHERE cd_grp_id='POSITION' AND dtl_cd='TL'", String.class
        )).isEqualTo("최종 이름");
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM cmn.cd_hist", Integer.class);
        putRows("POSITION", rows, 204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cmn.cd_hist", Integer.class)).isEqualTo(count);
    }

    @Test
    @Transactional
    void newCodeIsLastAndYAndMissingOrDuplicateRowsLeaveWholeSetUnchanged() throws Exception {
        ArrayNode rows = (ArrayNode) read("/api/admin/code-groups/JOB/codes");
        int max = 0;
        for (JsonNode row : rows) {
            ((ObjectNode) row).remove("cd_grp_id");
            max = Math.max(max, row.path("sort_seq").asInt());
        }
        rows.addObject().put("dtl_cd", "TEST1").put("cd_nm", "테스트").put("sort_seq", max + 1).put("use_yn", "N");
        putRows("JOB", rows, 204);
        JsonNode result = read("/api/admin/code-groups/JOB/codes");
        assertThat(result.get(12).path("dtl_cd").asText()).isEqualTo("TEST1");
        assertThat(result.get(12).path("use_yn").asText()).isEqualTo("Y");
        var codes = snapshot("cmn.cd_dtl", "cd_grp_id, dtl_cd");
        var history = snapshot("cmn.cd_hist", "cd_hist_id");
        ArrayNode missing = rows.deepCopy();
        missing.remove(0);
        putRows("JOB", missing, 400);
        ArrayNode duplicate = rows.deepCopy();
        duplicate.add(rows.get(0));
        putRows("JOB", duplicate, 409);
        assertThat(snapshot("cmn.cd_dtl", "cd_grp_id, dtl_cd")).isEqualTo(codes);
        assertThat(snapshot("cmn.cd_hist", "cd_hist_id")).isEqualTo(history);
    }

    @Test
    void groupHistoryFailureRollsBackActualCommittedTransaction() throws Exception {
        var groups = snapshot("cmn.cd_grp", "cd_grp_id");
        var histories = snapshot("cmn.cd_hist", "cd_hist_id");
        jdbc.execute("""
            ALTER TABLE cmn.cd_hist ADD CONSTRAINT ck_codegroup_history_failure
            CHECK (cd_grp_id <> 'TEST_ROLLBACK')
            """);
        try {
            mvc.perform(post("/api/admin/code-groups").contentType("application/json")
                .content("{\"cd_grp_id\":\"TEST_ROLLBACK\",\"cd_grp_nm\":\"롤백 검증\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false));
            assertThat(snapshot("cmn.cd_grp", "cd_grp_id")).isEqualTo(groups);
            assertThat(snapshot("cmn.cd_hist", "cd_hist_id")).isEqualTo(histories);
        } finally {
            jdbc.execute("ALTER TABLE cmn.cd_hist DROP CONSTRAINT ck_codegroup_history_failure");
        }
    }

    @Test
    void codeHistoryFailureRollsBackAllRowsAndAuditFields() throws Exception {
        var codes = snapshot("cmn.cd_dtl", "cd_grp_id, dtl_cd");
        var histories = snapshot("cmn.cd_hist", "cd_hist_id");
        ArrayNode rows = (ArrayNode) read("/api/admin/code-groups/POSITION/codes");
        for (JsonNode row : rows) {
            ((ObjectNode) row).remove("cd_grp_id");
            ((ObjectNode) row).put("cd_nm", row.path("cd_nm").asText() + " 변경");
        }
        jdbc.execute("""
            ALTER TABLE cmn.cd_hist ADD CONSTRAINT ck_codegroup_history_failure
            CHECK (cd_grp_id <> 'POSITION') NOT VALID
            """);
        try {
            putRows("POSITION", rows, 500);
            assertThat(snapshot("cmn.cd_dtl", "cd_grp_id, dtl_cd")).isEqualTo(codes);
            assertThat(snapshot("cmn.cd_hist", "cd_hist_id")).isEqualTo(histories);
        } finally {
            jdbc.execute("ALTER TABLE cmn.cd_hist DROP CONSTRAINT ck_codegroup_history_failure");
        }
    }

    private JsonNode read(String path) throws Exception {
        var result = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private void putRows(String group, ArrayNode rows, int expected) throws Exception {
        mvc.perform(put("/api/admin/code-groups/{group}/codes", group).contentType("application/json")
            .content(json.createObjectNode().set("codes", rows).toString()))
            .andExpect(status().is(expected));
    }
}
