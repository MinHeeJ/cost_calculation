package com.sicost.adminops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 이력 읽기 전용 어댑터로 쓰기 메서드를 제공하지 않는다. */
@Mapper
public interface CodeHistoryMapper {
    boolean groupExists(@Param("group") String group);

    long countHistory(@Param("group") String group);

    List<CodeHistoryRow> selectHistory(
        @Param("group") String group,
        @Param("pageLimit") int pageLimit,
        @Param("pageOffset") long pageOffset
    );
}
