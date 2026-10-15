package com.sicost.shared;

import com.sicost.shared.monthclose.*;
import com.sicost.common.DataScope;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sicost.common.GlobalExceptionHandler;
import com.sicost.common.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MonthCloseContractTest {
    private MonthCloseMapper mapper;
    private MonthCloseService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mapper = mock(MonthCloseMapper.class);
        service = spy(new MonthCloseService(mapper));
        mvc = MockMvcBuilders.standaloneSetup(new MonthCloseController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
    }

    @Test
    void publicClosedAndOpenMonthsAreReadableAndMissingMonthsAreExpanded() throws Exception {
        var closed = new MonthCloseRow();
        closed.setMonth("2026-09");
        closed.setStatus("CLOSED");
        when(mapper.selectRange("2026-09", "2026-11")).thenReturn(List.of(closed));
        mvc.perform(get("/api/month-close").param("from", "2026-09").param("to", "2026-11"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].month").value("2026-09"))
            .andExpect(jsonPath("$.data[0].status").value("CLOSED"))
            .andExpect(jsonPath("$.data[1].month").value("2026-10"))
            .andExpect(jsonPath("$.data[1].status").value("OPEN"))
            .andExpect(jsonPath("$.data[2].month").value("2026-11"));
        verify(service).getMonthClose("2026-09", "2026-11", DataScope.ALL);
        verify(mapper).selectRange("2026-09", "2026-11");
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({"2026-13,2026-10,from", "2026-00,2026-10,from", "26-01,2026-10,from",
        "2026-01,2026-1,to", "2026-10,2026-09,to", "2026-01,10000-01,to", "2026-01,2026-13,to"})
    void invalidRangeReturnsFieldWithoutQuery(String from, String to, String field) throws Exception {
        var result = mvc.perform(get("/api/month-close").param("from", from).param("to", to))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field))
            .andExpect(jsonPath("$.code").value("400")).andReturn();
        var body = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("requestId").asText()).isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        assertThat(body.path("meta").path("requestId").asText()).isEqualTo(body.path("requestId").asText());
        verifyNoInteractions(mapper);
    }

    @Test
    void missingFromReturns400From() throws Exception {
        mvc.perform(get("/api/month-close").param("to", "2026-10"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("from"));
        verifyNoInteractions(mapper);
    }

    @Test
    void missingToReturns400To() throws Exception {
        mvc.perform(get("/api/month-close").param("from", "2026-10"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("to"));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({"0000-01,0000-02,2", "9999-12,9999-12,1", "2025-12,2026-02,3"})
    void yearZeroLastYearAndYearBoundaryAreNotTimezoneConverted(String from, String to, int size) throws Exception {
        when(mapper.selectRange(from, to)).thenReturn(List.of());
        mvc.perform(get("/api/month-close").param("from", from).param("to", to))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(size))
            .andExpect(jsonPath("$.data[0].month").value(from))
            .andExpect(jsonPath("$.data[" + (size - 1) + "].month").value(to))
            .andExpect(jsonPath("$.data[0].status").value("OPEN"));
    }

    @Test
    void noArbitraryBusinessRangeLimitIsIntroduced() throws Exception {
        when(mapper.selectRange("0000-01", "9999-12")).thenReturn(List.of());
        mvc.perform(get("/api/month-close").param("from", "0000-01").param("to", "9999-12"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(120000));
    }

    @Test
    void mapperFailureDoesNotLeakSql() throws Exception {
        when(mapper.selectRange("2026-09", "2026-10"))
            .thenThrow(new IllegalStateException("SQL jdbc:postgresql://private password=secret"));
        var result = mvc.perform(get("/api/month-close").param("from", "2026-09").param("to", "2026-10"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQL", "jdbc", "password");
    }
}
