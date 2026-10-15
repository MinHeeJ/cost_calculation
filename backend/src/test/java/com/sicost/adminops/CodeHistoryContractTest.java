package com.sicost.adminops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.GlobalExceptionHandler;
import com.sicost.common.RequestIdFilter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CodeHistoryContractTest {
    private CodeHistoryMapper mapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mapper = mock(CodeHistoryMapper.class);
        mvc = MockMvcBuilders.standaloneSetup(new CodeHistoryController(new CodeHistoryService(mapper)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
    }

    @Test
    void anonymousPositionReadHasTypedSingleEnvelopeAndNullActorWithoutMutatingRow() throws Exception {
        var row = new CodeHistoryRow();
        row.setCdHistId(51L);
        row.setCdGrpId("POSITION");
        row.setCdGrpNm("직급");
        row.setDtlCd("TL");
        row.setCdChgTyCd("UPDATE");
        row.setItemNm("attr1_val");
        row.setBfrVal("200000");
        row.setAftVal("250000");
        row.setChgAt(LocalDateTime.of(2026, 10, 15, 9, 30));
        var json = new ObjectMapper().findAndRegisterModules();
        var before = json.valueToTree(row);
        when(mapper.groupExists("POSITION")).thenReturn(true);
        when(mapper.countHistory("POSITION")).thenReturn(1L);
        when(mapper.selectHistory("POSITION", 50, 0L)).thenReturn(List.of(row));
        var result = mvc.perform(get("/api/admin/code-history").param("cd_grp_id", "POSITION"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].cd_hist_id").value(51))
            .andExpect(jsonPath("$.data.items[0].dtl_cd").value("TL"))
            .andExpect(jsonPath("$.data.items[0].cd_chg_ty_cd").value("UPDATE"))
            .andExpect(jsonPath("$.data.items[0].item_nm").value("attr1_val"))
            .andExpect(jsonPath("$.data.items[0].bfr_val").value("200000"))
            .andExpect(jsonPath("$.data.items[0].aft_val").value("250000"))
            .andExpect(jsonPath("$.data.items[0].chg_user_id").isEmpty())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(50))
            .andExpect(jsonPath("$.data.sort").doesNotExist())
            .andExpect(jsonPath("$.data.data").doesNotExist()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("meta").path("requestId").asText())
            .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        com.fasterxml.jackson.databind.JsonNode after = json.valueToTree(row);
        assertThat(after).isEqualTo(before);
        verify(mapper).groupExists("POSITION");
        verify(mapper).countHistory("POSITION");
        verify(mapper).selectHistory("POSITION", 50, 0L);
        verifyNoMoreInteractions(mapper);
        // Durable contract fixture is loaded through the build unit's classpath, never the runner input tree.
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            assertThat(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("operationId: getCodeHistory", "CodeHistoryPage:");
        }
    }

    @Test
    void paramIsVirtualGroupAndEmptyUnfilteredPageIsSuccessful() throws Exception {
        var row = new CodeHistoryRow();
        row.setCdGrpId("PARAM");
        row.setCdGrpNm("기준값");
        when(mapper.countHistory("PARAM")).thenReturn(1L);
        when(mapper.selectHistory("PARAM", 20, 20L)).thenReturn(List.of(row));
        mvc.perform(get("/api/admin/code-history").param("cd_grp_id", "PARAM")
                .param("page", "2").param("size", "20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].cd_grp_nm").value("기준값"));
        verify(mapper, never()).groupExists(anyString());
        when(mapper.selectHistory(null, 50, 0L)).thenReturn(List.of());
        mvc.perform(get("/api/admin/code-history"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.total").value(0));
    }

    @ParameterizedTest
    @CsvSource({"page,0", "page,-1", "page,abc", "size,30", "size,0", "sort,'chg_at,asc'"})
    void invalidQueryReturnsFieldBeforePersistence(String field, String value) throws Exception {
        mvc.perform(get("/api/admin/code-history").param(field, value))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper);
    }

    @Test
    void overlongGroupIsBadRequest() throws Exception {
        mvc.perform(get("/api/admin/code-history").param("cd_grp_id", "X".repeat(31)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("cd_grp_id"));
        verifyNoInteractions(mapper);
    }

    @Test
    void missingGroupReturnsNotFoundWithoutHistoryQueries() throws Exception {
        mvc.perform(get("/api/admin/code-history").param("cd_grp_id", "MISSING"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.field").value("cd_grp_id"));
        verify(mapper).groupExists("MISSING");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void databaseFailureNeverLeaksSensitiveDetails() throws Exception {
        when(mapper.countHistory(null)).thenThrow(new IllegalStateException("SQL jdbc password=private"));
        var result = mvc.perform(get("/api/admin/code-history"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQL", "jdbc", "private");
    }
}
