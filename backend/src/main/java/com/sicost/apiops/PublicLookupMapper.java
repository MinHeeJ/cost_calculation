package com.sicost.apiops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 기존 공통코드와 기준값만 읽으며 감사 필드를 변경하지 않는다. */
@Mapper
public interface PublicLookupMapper {
    boolean groupExists(@Param("group") String group);

    List<CodeChoiceRow> selectChoices(@Param("group") String group);

    ParameterLookupRow selectParameter(@Param("key") String key, @Param("month") String month);
}
