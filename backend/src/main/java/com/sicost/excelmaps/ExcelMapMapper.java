package com.sicost.excelmaps;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 기존 cmn 테이블만 조회·저장하며 이력 INSERT는 호출자의 트랜잭션에 참여한다. */
@Mapper
public interface ExcelMapMapper {
    List<ExcelMapRow> selectAll();
    ExcelMapRow selectById(@Param("id") long id);
    int activeTarget(@Param("group") String group, @Param("code") String code);
    int insert(ExcelMapRow row);
    int update(@Param("id") long id, @Param("changes") java.util.Map<String, String> changes);
    int history(
        @Param("row") ExcelMapRow row,
        @Param("type") String type,
        @Param("item") String item,
        @Param("before") String before,
        @Param("after") String after,
        @Param("actor") Long actor
    );
}
