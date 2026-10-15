package com.sicost.shared.menu;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** sys.menu의 읽기 전용 어댑터이며 권한 시드는 조회 제한에 사용하지 않는다. */
@Mapper
public interface MenuMapper {
    List<MenuRow> selectActive();
}
