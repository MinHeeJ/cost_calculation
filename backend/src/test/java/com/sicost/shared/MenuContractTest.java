package com.sicost.shared;

import com.sicost.shared.menu.*;
import com.sicost.common.DataScope;
import java.util.List;
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

class MenuContractTest {
    private MenuMapper mapper;
    private MenuService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mapper = mock(MenuMapper.class);
        service = spy(new MenuService(mapper));
        mvc = MockMvcBuilders.standaloneSetup(new MenuController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter()).build();
    }

    private MenuRow row(long id, Long parent, int sort, String screen) {
        var row = new MenuRow();
        row.setMenuId(id);
        row.setParentId(parent);
        row.setName("메뉴" + id);
        row.setSortSeq(sort);
        row.setScreenId(screen);
        row.setMobileYn("Y");
        return row;
    }

    @Test
    void publicReadPreservesNullableFieldsBooleanAndSingleEnvelope() throws Exception {
        var root = row(1, null, 1, null);
        var leaf = row(2, 1L, 1, "UI-03");
        leaf.setMobileYn("N");
        when(mapper.selectActive()).thenReturn(List.of(root, leaf));
        var result = mvc.perform(get("/api/menus").header("X-Request-Id", "menus-contract"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].screenId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data[0].icon").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data[0].children[0].mobile").value(false))
            .andExpect(jsonPath("$.data[0].children[0].children").isEmpty()).andReturn();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo("menus-contract");
        assertThat(new ObjectMapper().readTree(result.getResponse().getContentAsByteArray())
            .path("meta").path("requestId").asText()).isEqualTo("menus-contract");
        verify(service).getMenus(DataScope.ALL);
        verify(mapper).selectActive();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void siblingSortUsesSequenceThenIdAndOrphansAreNotRoots() throws Exception {
        when(mapper.selectActive()).thenReturn(List.of(row(1, null, 1, null), row(4, 1L, 2, "UI-04"),
            row(3, 1L, 1, "UI-03"), row(2, 1L, 1, "UI-02"), row(5, 99L, 0, "UI-05")));
        mvc.perform(get("/api/menus")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].children.length()").value(3))
            .andExpect(jsonPath("$.data[0].children[0].menuId").value(2))
            .andExpect(jsonPath("$.data[0].children[1].menuId").value(3))
            .andExpect(jsonPath("$.data[0].children[2].menuId").value(4));
    }

    @Test
    void emptyMenusAreAnEmptyArrayNotNotFound() throws Exception {
        when(mapper.selectActive()).thenReturn(List.of());
        mvc.perform(get("/api/menus")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void databaseFailureUsesDirectSafeErrorAndMatchingIds() throws Exception {
        when(mapper.selectActive()).thenThrow(new IllegalStateException("SQL password=secret"));
        var result = mvc.perform(get("/api/menus")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("500"))
            .andExpect(jsonPath("$.field").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data").doesNotExist()).andReturn();
        var body = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("requestId").asText()).isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        assertThat(body.path("meta").path("requestId").asText()).isEqualTo(body.path("requestId").asText());
        assertThat(body.path("message").asText()).doesNotContain("SQL", "password", "secret");
    }
}
