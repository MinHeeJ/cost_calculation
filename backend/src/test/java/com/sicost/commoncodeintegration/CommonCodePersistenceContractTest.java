package com.sicost.commoncodeintegration;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real merged Spring/MyBatis HTTP workflows; absent PostgreSQL settings skip before context creation. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional
class CommonCodePersistenceContractTest {
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

    @Test
    void publicGroupCodeMapLifecycleSharesRowsAndAuditWithoutDeletingDisabledReferences() throws Exception {
        var menusBefore = jdbc.queryForList("SELECT * FROM sys.menu ORDER BY menu_id");
        var closedBefore = jdbc.queryForList("SELECT * FROM fin.mth_cls ORDER BY cls_ym");
        long previousHistory = jdbc.queryForObject("SELECT COUNT(*) FROM cmn.cd_hist", Long.class);
        empty(post("/api/admin/code-groups").contentType("application/json").content("""
            {"cd_grp_id":"COMMON_TEST","cd_grp_nm":"통합 공통코드","attr1_nm":"추가 값"}
            """));
        JsonNode group = find(data(get("/api/admin/code-groups")), "cd_grp_id", "COMMON_TEST");
        assertThat(group.path("cd_grp_se_cd").asText()).isEqualTo("BIZ");
        assertThat(group.path("code_count").asInt()).isZero();
        empty(put("/api/admin/code-groups/{group}/codes", "COMMON_TEST").contentType("application/json")
            .content("""
                {"codes":[{"dtl_cd":"ACTIVE","cd_nm":"활성 코드","sort_seq":1,"use_yn":"Y"}]}
                """));
        JsonNode choices = data(get("/api/codes/{group}", "COMMON_TEST"));
        assertThat(choices.size()).isEqualTo(1);
        assertThat(choices.get(0).path("code").asText()).isEqualTo("ACTIVE");
        empty(post("/api/admin/excel-maps").contentType("application/json").content("""
            {"excel_lbl_nm":"통합 표기","trns_cd_grp_id":"COMMON_TEST","trns_cd":"ACTIVE","map_role_cd":"SUB"}
            """));
        long id = jdbc.queryForObject("""
            SELECT map_id
            FROM cmn.excel_map
            WHERE trns_cd_grp_id = 'COMMON_TEST'
                AND trns_cd = 'ACTIVE'
            """, Long.class);
        JsonNode mapping = find(data(get("/api/admin/excel-maps")), "map_id", Long.toString(id));
        assertThat(mapping.path("cd_nm").asText()).isEqualTo("활성 코드");
        assertThat(mapping.path("cd_grp_nm").asText()).isEqualTo("통합 공통코드");
        empty(patch("/api/admin/excel-maps/{map_id}", id).contentType("application/json")
            .content("{\"use_yn\":\"N\"}"));
        setCodeUse("N");
        assertThat(data(get("/api/codes/{group}", "COMMON_TEST")).size()).isZero();
        JsonNode admin = data(get("/api/admin/code-groups/{group}/codes", "COMMON_TEST"));
        assertThat(admin.size()).isEqualTo(1);
        assertThat(admin.get(0).path("use_yn").asText()).isEqualTo("N");
        assertThat(find(data(get("/api/admin/excel-maps")), "map_id", Long.toString(id))
            .path("use_yn").asText()).isEqualTo("N");
        setCodeUse("Y");
        assertThat(data(get("/api/codes/{group}", "COMMON_TEST")).size()).isEqualTo(1);
        // Re-enabling a code must not re-enable a mapping independently disabled by its user.
        assertThat(find(data(get("/api/admin/excel-maps")), "map_id", Long.toString(id))
            .path("use_yn").asText()).isEqualTo("N");
        JsonNode history = data(get("/api/admin/code-history").param("cd_grp_id", "COMMON_TEST"));
        assertThat(history.path("total").asLong()).isEqualTo(6);
        assertThat(history.path("size").asInt()).isEqualTo(50);
        assertThat(history.path("items").size()).isEqualTo(6);
        boolean groupAdd = false;
        boolean mappingDisable = false;
        for (JsonNode item : history.path("items")) {
            assertThat(item.path("chg_user_id").isNull()).isTrue();
            assertThat(item.path("cd_grp_nm").asText()).isEqualTo("통합 공통코드");
            groupAdd |= item.path("dtl_cd").isNull() && "ADD".equals(item.path("cd_chg_ty_cd").asText());
            mappingDisable |= ("excel_map." + id + ".use_yn").equals(item.path("item_nm").asText())
                && "DISABLE".equals(item.path("cd_chg_ty_cd").asText());
        }
        assertThat(groupAdd).isTrue();
        assertThat(mappingDisable).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cmn.cd_hist", Long.class))
            .isEqualTo(previousHistory + 6);
        assertThat(jdbc.queryForList("SELECT * FROM sys.menu ORDER BY menu_id")).isEqualTo(menusBefore);
        assertThat(jdbc.queryForList("SELECT * FROM fin.mth_cls ORDER BY cls_ym")).isEqualTo(closedBefore);
    }

    @Test
    void standardWriteIsVisibleThroughPublicMonthLookupAndSharedParamHistoryOnlyForItsMonth() throws Exception {
        var closedBefore = jdbc.queryForList("SELECT * FROM fin.mth_cls ORDER BY cls_ym");
        empty(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
            .contentType("application/json").content("{\"aply_bgn_ym\":\"2026-11\",\"num_val\":11}"));
        assertThat(data(get("/api/params/{key}", "VAT_RATE").param("month", "2026-10"))
            .path("num_val").decimalValue()).isEqualByComparingTo("10");
        assertThat(data(get("/api/params/{key}", "VAT_RATE").param("month", "2026-11"))
            .path("num_val").decimalValue()).isEqualByComparingTo("11");
        JsonNode versions = data(get("/api/admin/operating-standards/{oprt_std_id}/history", "VAT_RATE"));
        assertThat(find(versions, "aply_bgn_ym", "2026-11").path("unit_nm").asText()).isEqualTo("%");
        JsonNode audit = data(get("/api/admin/code-history").param("cd_grp_id", "PARAM"));
        boolean found = false;
        for (JsonNode item : audit.path("items")) {
            if ("VAT_RATE".equals(item.path("dtl_cd").asText())
                && "2026-11".equals(item.path("item_nm").asText())) {
                found = true;
                assertThat(item.path("cd_grp_nm").asText()).isEqualTo("기준값");
                assertThat(item.path("cd_chg_ty_cd").asText()).isEqualTo("UPDATE");
                assertThat(item.path("aft_val").asText()).isEqualTo("11");
                assertThat(item.path("chg_user_id").isNull()).isTrue();
            }
        }
        assertThat(found).isTrue();
        var before = jdbc.queryForList("SELECT * FROM cmn.oprt_std ORDER BY oprt_std_id, aply_bgn_ym");
        var historyBefore = jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id");
        mvc.perform(put("/api/admin/operating-standards/{oprt_std_id}", "VAT_RATE")
                .contentType("application/json").content("{\"aply_bgn_ym\":\"2026-09\",\"num_val\":12}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));
        assertThat(jdbc.queryForList("SELECT * FROM cmn.oprt_std ORDER BY oprt_std_id, aply_bgn_ym"))
            .isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id")).isEqualTo(historyBefore);
        assertThat(jdbc.queryForList("SELECT * FROM fin.mth_cls ORDER BY cls_ym")).isEqualTo(closedBefore);
    }

    @Test
    void systemMixedForbiddenChangeKeepsEntireDatabaseSnapshotAndPublicName() throws Exception {
        List<Map<String, Object>> before = jdbc.queryForList("""
            SELECT *
            FROM cmn.cd_dtl
            WHERE cd_grp_id = 'CONTRACT_TYPE'
            ORDER BY dtl_cd
            """);
        var historyBefore = jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id");
        ArrayNode rows = (ArrayNode) data(get("/api/admin/code-groups/{group}/codes", "CONTRACT_TYPE"));
        for (JsonNode row : rows) ((ObjectNode) row).remove("cd_grp_id");
        ObjectNode target = (ObjectNode) find(rows, "dtl_cd", "TK");
        target.put("cd_nm", "허용 이름 변경").put("use_yn", "N");
        mvc.perform(put("/api/admin/code-groups/{group}/codes", "CONTRACT_TYPE")
                .contentType("application/json").content(json.createObjectNode().set("codes", rows).toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SYSTEM_CODE_LOCKED"));
        assertThat(jdbc.queryForList("""
            SELECT *
            FROM cmn.cd_dtl
            WHERE cd_grp_id = 'CONTRACT_TYPE'
            ORDER BY dtl_cd
            """)).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id")).isEqualTo(historyBefore);
        JsonNode choice = find(data(get("/api/codes/{group}", "CONTRACT_TYPE")), "code", "TK");
        assertThat(choice.path("name").asText()).isNotEqualTo("허용 이름 변경");
    }

    private void setCodeUse(String use) throws Exception {
        empty(put("/api/admin/code-groups/{group}/codes", "COMMON_TEST").contentType("application/json")
            .content(json.writeValueAsString(Map.of("codes", List.of(Map.of(
                "dtl_cd", "ACTIVE", "cd_nm", "활성 코드", "sort_seq", 1, "use_yn", use))))));
    }

    private JsonNode find(JsonNode rows, String field, String value) {
        for (JsonNode row : rows) {
            if (value.equals(row.path(field).asText())) return row;
        }
        throw new AssertionError("Missing " + field + "=" + value + " in " + rows);
    }

    private JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        var response = mvc.perform(request).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andReturn().getResponse();
        JsonNode body = json.readTree(response.getContentAsByteArray());
        assertThat(body.path("meta").path("requestId").asText())
            .isEqualTo(response.getHeader("X-Request-Id")).isNotBlank();
        return body.path("data");
    }

    private void empty(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().isNoContent()).andExpect(content().bytes(new byte[0]))
            .andExpect(header().exists("X-Request-Id"));
    }
}
