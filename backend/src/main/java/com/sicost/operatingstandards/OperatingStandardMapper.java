package com.sicost.operatingstandards;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 기존 cmn/fin 테이블의 기준값과 원자 변경이력을 연결하는 XML 매퍼이다. */
@Mapper
public interface OperatingStandardMapper {
    List<OperatingStandardRow> selectCurrent(@Param("month") String month);

    List<OperatingStandardRow> selectHistory(@Param("key") String key);

    String selectLastClosedMonth();

    /** 자연키 충돌 시 요청 적용월의 값만 바꾸고 기존 감사·메타는 보존한다. */
    int save(
        @Param("row") OperatingStandardRow row,
        @Param("actor") Long actor
    );

    int appendHistory(
        @Param("key") String key,
        @Param("month") String month,
        @Param("before") String before,
        @Param("after") String after,
        @Param("actor") Long actor
    );
}
