package com.sicost.codegroups;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 공통코드 행과 append-only 이력을 같은 JDBC 트랜잭션에서 다룬다. */
@Mapper
public interface CodeGroupMapper {
    List<CodeGroupRow> selectGroups();
    CodeGroupRow selectGroup(@Param("group") String group);
    List<AdminCodeRow> selectCodes(@Param("group") String group);
    int insertGroup(@Param("row") CodeGroupRow row, @Param("actor") Long actor);
    int insertCode(@Param("row") AdminCodeRow row, @Param("actor") Long actor);
    int updateCode(@Param("row") AdminCodeRow row, @Param("changes") Map<String, Object> changes,
        @Param("actor") Long actor);
    int insertHistory(@Param("group") String group, @Param("code") String code,
        @Param("type") String type, @Param("item") String item,
        @Param("before") String before, @Param("after") String after, @Param("actor") Long actor);
}
