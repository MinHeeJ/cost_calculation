package com.sicost.shared.menu;

import java.util.List;

/** 사용 중 메뉴의 nullable 메타데이터와 자식 트리를 전달한다. */
public record MenuNode(Long menuId, String name, String screenId, String icon,
                       boolean mobile, List<MenuNode> children) {
}
