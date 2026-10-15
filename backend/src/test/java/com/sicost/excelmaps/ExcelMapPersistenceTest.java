package com.sicost.excelmaps;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 Spring proxy·PostgreSQL mapper·생성 키·이력 실패 롤백을 명시된 격리 DB에서만 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ExcelMapPersistenceTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "sicost"));
        registry.add("spring.datasource.password", () ->
            System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "dev-only-password"));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @SpyBean ExcelMapMapper mapper;
    private final ObjectMapper json = new ObjectMapper();
    private String label;
    private List<Map<String, Object>> business;

    @BeforeEach
    void setup() {
        label = "계약시험-" + java.util.UUID.randomUUID();
        business = jdbc.queryForList("SELECT * FROM fin.mth_cls ORDER BY cls_ym");
    }

    @AfterEach
    void cleanup() {
        var ids = jdbc.queryForList("SELECT map_id FROM cmn.excel_map WHERE excel_lbl_nm = ?", Long.class, label);
        for (Long id : ids) {
            jdbc.update("DELETE FROM cmn.cd_hist WHERE item_nm = ? OR item_nm LIKE ?",
                "excel_map." + id, "excel_map." + id + ".%");
        }
        jdbc.update("DELETE FROM cmn.excel_map WHERE excel_lbl_nm = ?", label);
        assertThat(jdbc.queryForList("SELECT * FROM fin.mth_cls ORDER BY cls_ym")).isEqualTo(business);
    }

    private void create(String code) throws Exception {
        String body = json.writeValueAsString(Map.of("excel_lbl_nm", label, "trns_cd_grp_id", "JOB",
            "trns_cd", code, "map_role_cd", "MAIN"));
        mvc.perform(post("/api/admin/excel-maps").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNoContent()).andExpect(content().bytes(new byte[0]));
    }

    @Test
    void realGeneratedKeyJoinsHistoryAndReadIsSideEffectFreeIncludingInactiveRows() throws Exception {
        create("BE");
        create("OPS");
        Long id = jdbc.queryForObject(
            "SELECT map_id FROM cmn.excel_map WHERE excel_lbl_nm = ? AND trns_cd = 'BE'", Long.class, label);
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"use_yn\":\"N\",\"map_role_cd\":\"SUB\"}"))
            .andExpect(status().isNoContent());
        var rows = jdbc.queryForList("SELECT * FROM cmn.excel_map ORDER BY map_id");
        var histories = jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id");
        var result = mvc.perform(get("/api/admin/excel-maps"))
            .andExpect(status().isOk()).andReturn();
        var data = json.readTree(result.getResponse().getContentAsByteArray()).path("data");
        boolean found = false;
        for (var row : data) {
            if (row.path("map_id").asLong() == id) {
                found = true;
                assertThat(row.path("use_yn").asText()).isEqualTo("N");
                assertThat(row.path("map_role_cd").asText()).isEqualTo("SUB");
                assertThat(row.path("cd_nm").asText()).isNotBlank();
                assertThat(row.path("cd_grp_nm").asText()).isNotBlank();
            }
        }
        assertThat(found).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM cmn.excel_map ORDER BY map_id")).isEqualTo(rows);
        assertThat(jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id")).isEqualTo(histories);
        var history = jdbc.queryForMap("SELECT * FROM cmn.cd_hist WHERE item_nm = ?", "excel_map." + id);
        assertThat(history).containsEntry("cd_grp_id", "JOB").containsEntry("dtl_cd", "BE")
            .containsEntry("cd_chg_ty_cd", "ADD").containsEntry("chg_user_id", null);
    }

    @Test
    void historyFailureRollsBackCreateThroughRealTransaction() throws Exception {
        var before = jdbc.queryForList("SELECT * FROM cmn.excel_map ORDER BY map_id");
        var histories = jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id");
        failHistory();
        mvc.perform(post("/api/admin/excel-maps").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("excel_lbl_nm", label, "trns_cd_grp_id", "JOB",
                "trns_cd", "BE", "map_role_cd", "MAIN"))))
            .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForList("SELECT * FROM cmn.excel_map ORDER BY map_id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id")).isEqualTo(histories);
    }

    @Test
    void historyFailureRollsBackAllPatchFieldsThroughRealTransaction() throws Exception {
        create("BE");
        Long id = jdbc.queryForObject("SELECT map_id FROM cmn.excel_map WHERE excel_lbl_nm = ?", Long.class, label);
        var before = jdbc.queryForList("SELECT * FROM cmn.excel_map ORDER BY map_id");
        var histories = jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id");
        failHistory();
        mvc.perform(patch("/api/admin/excel-maps/{map_id}", id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"map_role_cd\":\"SUB\",\"rmrk_desc\":\"롤백\",\"use_yn\":\"N\"}"))
            .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForList("SELECT * FROM cmn.excel_map ORDER BY map_id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM cmn.cd_hist ORDER BY cd_hist_id")).isEqualTo(histories);
    }

    private void failHistory() {
        doThrow(new IllegalStateException("injected persistence failure"))
            .when(mapper).history(any(), anyString(), anyString(), nullable(String.class),
                nullable(String.class), nullable(Long.class));
    }
}
