package com.sicost.apiops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 기존 cmn 테이블만 읽는 공개 소비용 mapper이다. 쓰기나 이력 생성은 제공하지 않는다. */
@Mapper
public interface CodeLookupMapper {
    boolean groupExists(@Param("group") String group);

    /** 선택 가능한 코드만 sort_seq, dtl_cd 순으로 조회한다. */
    List<CodeChoiceRow> selectChoices(@Param("group") String group);

    /** 현재 시각이 아닌 요청 월 이하에서 가장 최근 적용 행을 조회한다. */
    ParameterRow selectParameter(@Param("key") String key, @Param("month") String month);
}
