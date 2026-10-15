package com.sicost.shared.menu;

import com.sicost.common.ApiResponse;
import com.sicost.common.DataScope;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 없는 메뉴 조회를 공통 추적 봉투에 연결한다. */
@RestController
public class MenuController {
    private final MenuService service;

    public MenuController(MenuService service) {
        this.service = service;
    }

    /** 전체 메뉴 트리는 비페이지이며 고정 ALL 데이터 범위로 조회한다. */
    @GetMapping("/api/menus")
    public ApiResponse<List<MenuNode>> getMenus(HttpServletRequest request) {
        return ApiResponse.ok(service.getMenus(DataScope.ALL), RequestIdFilter.requestId(request));
    }
}
