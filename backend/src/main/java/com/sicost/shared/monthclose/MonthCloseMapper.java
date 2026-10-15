package com.sicost.shared.monthclose;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** fin.mth_cls의 자연키 범위만 SELECT하며 누락 월을 저장하지 않는다. */
@Mapper
public interface MonthCloseMapper {
    List<MonthCloseRow> selectRange(@Param("from") String from, @Param("to") String to);
}
