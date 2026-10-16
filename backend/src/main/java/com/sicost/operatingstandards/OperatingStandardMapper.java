package com.sicost.operatingstandards;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 기존 복합키와 PARAM 감사 테이블만 사용하는 MyBatis 경계다. */
@Mapper
public interface OperatingStandardMapper {
    List<OperatingStandardRow> selectCurrent(@Param("month") String month);
    List<OperatingStandardRow> selectHistory(@Param("key") String key);
    String selectLastClosed();
    int upsert(@Param("row") OperatingStandardRow row, @Param("actor") Long actor);
    int insertHistory(
        @Param("key") String key,
        @Param("month") String month,
        @Param("before") String before,
        @Param("after") String after,
        @Param("actor") Long actor
    );
}
