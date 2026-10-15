package com.sicost.shared.menu;

import com.sicost.common.DataScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 한 읽기 트랜잭션에서 메뉴 트리를 구성하며 부모 없는 노드는 승격하지 않는다. */
@Service
public class MenuService {
    private final MenuMapper mapper;

    public MenuService(MenuMapper mapper) {
        this.mapper = mapper;
    }

    /** 현재 호출자는 ALL이며 미래 역할 정책이나 쓰기를 도입하지 않는다. */
    @Transactional(readOnly = true)
    public List<MenuNode> getMenus(DataScope scope) {
        var rows = new ArrayList<>(mapper.selectActive());
        rows.sort(Comparator.comparing(MenuRow::getSortSeq).thenComparing(MenuRow::getMenuId));
        return children(rows, null, new HashSet<>());
    }

    private List<MenuNode> children(List<MenuRow> rows, Long parent, Set<Long> ancestors) {
        List<MenuNode> result = new ArrayList<>();
        for (MenuRow row : rows) {
            if (!java.util.Objects.equals(parent, row.getParentId()) || ancestors.contains(row.getMenuId())) {
                continue;
            }
            var path = new HashSet<>(ancestors);
            path.add(row.getMenuId());
            result.add(new MenuNode(row.getMenuId(), row.getName(), row.getScreenId(), row.getIcon(),
                "Y".equals(row.getMobileYn()), children(rows, row.getMenuId(), path)));
        }
        return result;
    }
}
