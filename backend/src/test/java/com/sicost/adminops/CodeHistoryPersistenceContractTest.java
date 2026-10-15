package com.sicost.adminops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 PostgreSQL이 제공된 경우에만 SQL materialization·페이지·불변성을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional
class CodeHistoryPersistenceContractTest {
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
    @Autowired RequestMappingHandlerMapping mappings;

    private List<Map<String, Object>> snapshot(String table, String key) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY " + key);
    }

    private JsonNode page(String group, int page) throws Exception {
        var result = mvc.perform(get("/api/admin/code-history")
                .param("cd_grp_id", group).param("page", Integer.toString(page)))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    @Test
    void fiftyOneHistoryRowsHaveStablePagesMatchingCountAndReadNeverChangesAnyBusinessOrAuditField()
        throws Exception {
        jdbc.update("DELETE FROM cmn.cd_hist");
        // Seeded POSITION/TL is reused; no production seed or sibling write operation is introduced.
        jdbc.update("UPDATE cmn.cd_dtl SET attr1_val = '250000' WHERE cd_grp_id = 'POSITION' AND dtl_cd = 'TL'");
        for (int i = 0; i < 51; i++) {
            jdbc.update("""
                INSERT INTO cmn.cd_hist (
                    cd_grp_id, dtl_cd, cd_chg_ty_cd, item_nm, bfr_val, aft_val, chg_user_id, chg_at
                )
                VALUES ('POSITION', 'TL', 'UPDATE', 'attr1_val', '200000', '250000', NULL,
                    TIMESTAMP '2026-10-15 09:30:00')
                """);
        }
        jdbc.update("""
            INSERT INTO cmn.cd_hist (cd_grp_id, dtl_cd, cd_chg_ty_cd, item_nm, chg_at)
            VALUES ('PARAM', 'VAT_RATE', 'UPDATE', '적용 시작월', TIMESTAMP '2026-10-15 09:30:00')
            """);
        var tables = Map.of(
            "cmn.cd_grp", "cd_grp_id",
            "cmn.cd_dtl", "cd_grp_id, dtl_cd",
            "cmn.excel_map", "map_id",
            "cmn.oprt_std", "oprt_std_id, aply_bgn_ym",
            "cmn.cd_hist", "cd_hist_id",
            "fin.mth_cls", "cls_ym"
        );
        var before = new java.util.HashMap<String, List<Map<String, Object>>>();
        tables.forEach((table, key) -> before.put(table, snapshot(table, key)));
        var first = page("POSITION", 1);
        var second = page("POSITION", 2);
        assertThat(first.path("items").size()).isEqualTo(50);
        assertThat(second.path("items").size()).isEqualTo(1);
        assertThat(first.path("total").asLong()).isEqualTo(51);
        assertThat(second.path("total").asLong()).isEqualTo(51);
        assertThat(first.path("size").asInt()).isEqualTo(50);
        var row = first.path("items").get(0);
        assertThat(row.path("dtl_cd").asText()).isEqualTo("TL");
        assertThat(row.path("cd_chg_ty_cd").asText()).isEqualTo("UPDATE");
        assertThat(row.path("item_nm").asText()).isEqualTo("attr1_val");
        assertThat(row.path("bfr_val").asText()).isEqualTo("200000");
        assertThat(row.path("aft_val").asText()).isEqualTo("250000");
        assertThat(row.path("chg_user_id").isNull()).isTrue();
        assertThat(row.path("chg_at").asText()).isEqualTo("2026-10-15T09:30:00");
        var ids = new ArrayList<Long>();
        first.path("items").forEach(item -> ids.add(item.path("cd_hist_id").asLong()));
        second.path("items").forEach(item -> ids.add(item.path("cd_hist_id").asLong()));
        assertThat(ids).hasSize(51).doesNotHaveDuplicates().isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(page("PARAM", 1).path("items").get(0).path("cd_grp_nm").asText()).isEqualTo("기준값");
        mvc.perform(get("/api/admin/code-history"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(52));
        tables.forEach((table, key) -> assertThat(snapshot(table, key)).isEqualTo(before.get(table)));
        // Inspect actual registered HTTP handlers: history is read-only, not merely missing buttons.
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (mapping.getPatternValues().stream().anyMatch(path -> path.startsWith("/api/admin/code-history"))) {
                assertThat(mapping.getMethodsCondition().getMethods())
                    .containsExactly(org.springframework.web.bind.annotation.RequestMethod.GET);
            }
        });
    }

    @Test
    void emptyExistingGroupAndOutOfRangePageReturnEmptySuccess() throws Exception {
        jdbc.update("DELETE FROM cmn.cd_hist");
        assertThat(page("POSITION", 1).path("items")).isEmpty();
        assertThat(page("POSITION", Integer.MAX_VALUE).path("items")).isEmpty();
    }
}
