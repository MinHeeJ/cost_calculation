package com.sicost.codegroups;

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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PostgreSQL/Flyway/실제 HTTP 원자성 검증. DB가 없으면 context 생성 전에 명시적으로 건너뛴다. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional
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
    @Autowired ObjectMapper json;
    @SpyBean CodeGroupMapper mapper;

    private List<Map<String, Object>> codes() {
        return jdbc.queryForList("SELECT * FROM cmn.cd_dtl ORDER BY cd_grp_id, dtl_cd");
    }

    private List<Map<String, Object>> history() {
        return jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id");
    }

    private ObjectNode body(String group) throws Exception {
        var response = mvc.perform(get("/api/admin/code-groups/{group}/codes", group))
            .andExpect(status().isOk()).andReturn().getResponse();
        ArrayNode rows = (ArrayNode) json.readTree(response.getContentAsByteArray()).get("data");
        for (var row : rows) ((ObjectNode) row).remove("cd_grp_id");
        return json.createObjectNode().set("codes", rows);
    }

    @Test
    void seededReadsArePublicSortedAndAuditImmutable() throws Exception {
        var before = codes();
        var audit = history();
        var response = mvc.perform(get("/api/admin/code-groups")).andExpect(status().isOk()).andReturn();
        var groups = json.readTree(response.getResponse().getContentAsByteArray()).get("data");
        int previous = Integer.MIN_VALUE;
        for (var group : groups) {
            assertThat(group.get("sort_seq").asInt()).isGreaterThanOrEqualTo(previous);
            previous = group.get("sort_seq").asInt();
            if (group.get("cd_grp_id").asText().equals("JOB")) {
                assertThat(group.get("code_count").asInt()).isEqualTo(12);
                assertThat(group.get("attr1_nm").asText()).isEqualTo("기본 단가(원)");
            }
            if (group.get("cd_grp_id").asText().equals("CONTRACT_TYPE")) {
                assertThat(group.get("cd_grp_se_cd").asText()).isEqualTo("SYSTEM");
            }
        }
        mvc.perform(get("/api/admin/code-groups/{group}/codes", "JOB"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(12));
        mvc.perform(get("/api/admin/code-history")).andExpect(status().isOk());
        assertThat(codes()).isEqualTo(before);
        assertThat(history()).isEqualTo(audit);
    }

    @Test
    void createGroupAppendsBizAndExactlyOneNullCodeAudit() throws Exception {
        int max = jdbc.queryForObject("SELECT MAX(sort_seq) FROM cmn.cd_grp", Integer.class);
        var before = codes();
        mvc.perform(post("/api/admin/code-groups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cd_grp_id\":\"TEST_GROUP\",\"cd_grp_nm\":\"테스트 그룹\"}"))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(jdbc.queryForMap("SELECT cd_grp_se_cd, sort_seq, created_by FROM cmn.cd_grp "
            + "WHERE cd_grp_id='TEST_GROUP'"))
            .containsEntry("cd_grp_se_cd", "BIZ").containsEntry("sort_seq", max + 1).containsEntry("created_by", null);
        assertThat(jdbc.queryForList("SELECT dtl_cd, cd_chg_ty_cd, chg_user_id FROM cmn.cd_hist "
            + "WHERE cd_grp_id='TEST_GROUP'"))
            .singleElement().satisfies(row -> assertThat(row).containsEntry("dtl_cd", null)
                .containsEntry("cd_chg_ty_cd", "ADD").containsEntry("chg_user_id", null));
        assertThat(codes()).isEqualTo(before);
    }

    @Test
    void fullSetInsertDisableEnableAndLastSuccessfulWritePersistWithAudit() throws Exception {
        ObjectNode body = body("JOB");
        ArrayNode rows = (ArrayNode) body.get("codes");
        int max = 0;
        for (var row : rows) max = Math.max(max, row.get("sort_seq").asInt());
        rows.addObject().put("dtl_cd", "TEST1").put("cd_nm", "테스트").put("sort_seq", max + 1).put("use_yn", "Y");
        save("JOB", body);
        assertThat(jdbc.queryForMap("SELECT use_yn, created_by FROM cmn.cd_dtl "
            + "WHERE cd_grp_id='JOB' AND dtl_cd='TEST1'"))
            .containsEntry("use_yn", "Y").containsEntry("created_by", null);
        for (var row : rows) {
            if (row.get("dtl_cd").asText().equals("OPS")) ((ObjectNode) row).put("use_yn", "N");
        }
        save("JOB", body);
        assertThat(jdbc.queryForObject("SELECT use_yn FROM cmn.cd_dtl "
            + "WHERE cd_grp_id='JOB' AND dtl_cd='OPS'", String.class)).isEqualTo("N");
        for (var row : rows) {
            if (row.get("dtl_cd").asText().equals("OPS")) ((ObjectNode) row).put("use_yn", "Y");
        }
        save("JOB", body);
        var added = (ObjectNode) rows.get(rows.size() - 1);
        added.put("cd_nm", "첫 저장");
        save("JOB", body);
        added.put("cd_nm", "마지막 저장");
        save("JOB", body);
        assertThat(jdbc.queryForObject("SELECT cd_nm FROM cmn.cd_dtl "
            + "WHERE cd_grp_id='JOB' AND dtl_cd='TEST1'", String.class)).isEqualTo("마지막 저장");
        assertThat(jdbc.queryForList("SELECT cd_chg_ty_cd FROM cmn.cd_hist "
            + "WHERE cd_grp_id='JOB' AND dtl_cd='OPS' ORDER BY cd_hist_id", String.class))
            .containsSubsequence("DISABLE", "ENABLE");
    }

    @Test
    void positionUpdateWritesExactAuditAndNoOpDoesNotTouchRows() throws Exception {
        var body = body("POSITION");
        for (var row : body.get("codes")) {
            if (row.get("dtl_cd").asText().equals("TL")) ((ObjectNode) row).put("attr1_val", "250000");
        }
        save("POSITION", body);
        mvc.perform(get("/api/admin/code-history").param("cd_grp_id", "POSITION"))
            .andExpect(jsonPath("$.data.items[0].dtl_cd").value("TL"))
            .andExpect(jsonPath("$.data.items[0].bfr_val").value("200000"))
            .andExpect(jsonPath("$.data.items[0].aft_val").value("250000"))
            .andExpect(jsonPath("$.data.items[0].item_nm").value("attr1_val"))
            .andExpect(jsonPath("$.data.items[0].chg_user_id").isEmpty());
        var snapshot = codes();
        var audit = history();
        save("POSITION", body);
        assertThat(codes()).isEqualTo(snapshot);
        assertThat(history()).isEqualTo(audit);
    }

    @Test
    void invalidLastRowOrMissingCodeDoesNotPartiallyUpdate() throws Exception {
        var body = body("JOB");
        var rows = (ArrayNode) body.get("codes");
        ((ObjectNode) rows.get(0)).put("cd_nm", "저장되면 안 됨");
        rows.remove(rows.size() - 1);
        var before = codes();
        var audit = history();
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "JOB")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("codes"));
        assertThat(codes()).isEqualTo(before);
        assertThat(history()).isEqualTo(audit);
    }

    @Test
    void fiftyOneHistoryRowsAreStablyPagedAndParamNameIsProjectedWithoutMutation() throws Exception {
        for (int i = 0; i < 51; i++) {
            jdbc.update("INSERT INTO cmn.cd_hist(cd_grp_id, dtl_cd, cd_chg_ty_cd, item_nm) "
                + "VALUES ('PARAM','VAT_RATE','UPDATE',?)", String.valueOf(i));
        }
        var before = history();
        var first = mvc.perform(get("/api/admin/code-history").param("cd_grp_id", "PARAM"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(50))
            .andExpect(jsonPath("$.data.total").value(51))
            .andExpect(jsonPath("$.data.items[0].cd_grp_nm").value("기준값")).andReturn();
        var second = mvc.perform(get("/api/admin/code-history").param("cd_grp_id", "PARAM").param("page", "2"))
            .andExpect(jsonPath("$.data.items.length()").value(1)).andReturn();
        long firstId = json.readTree(first.getResponse().getContentAsByteArray())
            .path("data").path("items").get(49).path("cd_hist_id").asLong();
        long lastId = json.readTree(second.getResponse().getContentAsByteArray())
            .path("data").path("items").get(0).path("cd_hist_id").asLong();
        assertThat(firstId).isGreaterThan(lastId);
        assertThat(history()).isEqualTo(before);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackRealRequestTransactionForCodeAndGroup() throws Exception {
        var before = codes();
        var audit = history();
        var groups = jdbc.queryForList("SELECT * FROM cmn.cd_grp ORDER BY cd_grp_id");
        var body = body("POSITION");
        ((ObjectNode) body.get("codes").get(0)).put("cd_nm", "rollback");
        doThrow(new IllegalStateException("audit rejected")).when(mapper).insertHistory(anyMap());
        try {
            mvc.perform(put("/api/admin/code-groups/{group}/codes", "POSITION")
                    .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isInternalServerError());
            assertThat(codes()).isEqualTo(before);
            mvc.perform(post("/api/admin/code-groups").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cd_grp_id\":\"TEST_GROUP\",\"cd_grp_nm\":\"테스트 그룹\"}"))
                .andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForList("SELECT * FROM cmn.cd_grp ORDER BY cd_grp_id")).isEqualTo(groups);
            assertThat(history()).isEqualTo(audit);
        } finally {
            reset(mapper);
        }
    }

    private void save(String group, ObjectNode body) throws Exception {
        mvc.perform(put("/api/admin/code-groups/{group}/codes", group)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
    }
}
