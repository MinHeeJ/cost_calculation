package com.sicost.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.shared.menu.MenuService;
import com.sicost.common.DataScope;
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

/** PostgreSQL16が明示された時だけ実Mapper/HTTPと監査不変性を検証する。 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional
class SharedPersistenceContractTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "sicost"));
        registry.add("spring.datasource.password", () ->
            System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "dev-only-password"));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MenuService menus;

    private java.util.List<Map<String, Object>> snapshot(String table, String key) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY " + key);
    }

    @Test
    void seedMenuTreeHas31ScreensAndPublicReadDoesNotChangeAuditOrRolePermissions() throws Exception {
        var before = snapshot("sys.menu", "menu_id");
        var permissions = snapshot("sys.role_menu_auth", "role_id, menu_id");
        assertThat(before).hasSize(41);
        assertThat(menus.getMenus(DataScope.ALL)).hasSize(10);
        var result = mvc.perform(get("/api/menus")).andExpect(status().isOk()).andReturn();
        var data = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray()).path("data");
        int count = 0;
        for (var group : data) {
            assertThat(group.path("screenId").isNull()).isTrue();
            for (var leaf : group.path("children")) {
                assertThat(leaf.path("screenId").asText()).startsWith("UI-");
                assertThat(leaf.path("children").isArray()).isTrue();
                count++;
            }
        }
        assertThat(count).isEqualTo(31);
        assertThat(snapshot("sys.menu", "menu_id")).isEqualTo(before);
        assertThat(snapshot("sys.role_menu_auth", "role_id, menu_id")).isEqualTo(permissions);
    }

    @Test
    void inactiveParentAndLeafAreExcludedAndSiblingsFollowSortThenId() throws Exception {
        jdbc.update("UPDATE sys.menu SET use_yn = 'N' WHERE menu_id = 2");
        jdbc.update("UPDATE sys.menu SET sort_seq = 0 WHERE scrn_id IN ('UI-01', 'UI-31')");
        jdbc.update("UPDATE sys.menu SET use_yn = 'N' WHERE scrn_id = 'UI-05'");
        var before = snapshot("sys.menu", "menu_id");
        var result = mvc.perform(get("/api/menus")).andExpect(status().isOk()).andReturn();
        var data = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray()).path("data");
        for (var group : data) {
            assertThat(group.path("menuId").asLong()).isNotEqualTo(2);
            for (var leaf : group.path("children")) {
                assertThat(leaf.path("screenId").asText()).isNotIn("UI-15", "UI-02", "UI-05");
            }
            if (group.path("menuId").asLong() == 3) {
                assertThat(group.path("children").get(0).path("screenId").asText()).isEqualTo("UI-01");
                assertThat(group.path("children").get(1).path("screenId").asText()).isEqualTo("UI-31");
            }
        }
        assertThat(snapshot("sys.menu", "menu_id")).isEqualTo(before);
    }

    @Test
    void monthReadsUseSeedStateAndNeverInsertMissingMonthsOrChangeCloseReleaseFields() throws Exception {
        var before = snapshot("fin.mth_cls", "cls_ym");
        mvc.perform(get("/api/month-close").param("from", "2026-09").param("to", "2026-11"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].status").value("CLOSED"))
            .andExpect(jsonPath("$.data[1].status").value("OPEN"))
            .andExpect(jsonPath("$.data[2].month").value("2026-11"))
            .andExpect(jsonPath("$.data[2].status").value("OPEN"));
        assertThat(snapshot("fin.mth_cls", "cls_ym")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fin.mth_cls WHERE cls_ym = '2026-11'", Long.class))
            .isZero();
    }
}
