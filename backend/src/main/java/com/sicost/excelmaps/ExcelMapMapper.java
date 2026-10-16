package com.sicost.excelmaps;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Existing cmn tables only; all mutations participate in the caller's transaction. */
@Mapper
public interface ExcelMapMapper {
    List<ExcelMapRow> selectAll();
    ExcelMapRow selectById(@Param("id") long id);
    String selectCodeUse(@Param("group") String group, @Param("code") String code);
    int countDuplicate(ExcelMapRow row);
    int insert(@Param("row") ExcelMapRow row, @Param("actor") Long actor);
    int update(ExcelMapRow row);
    int insertHistory(
        @Param("row") ExcelMapRow row,
        @Param("type") String type,
        @Param("item") String item,
        @Param("before") String before,
        @Param("after") String after,
        @Param("actor") Long actor
    );
}
