package com.sicost.codegroups;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** cmn 기존 자연키로 전체 목록을 읽고 변경 컬럼과 이력을 같은 연결에서 기록한다. */
@Mapper
public interface CodeGroupMapper {
    List<CodeGroupRow> selectGroups();
    CodeGroupRow selectGroup(@Param("group") String group);
    List<CodeDetailRow> selectCodes(@Param("group") String group);
    int insertGroup(Map<String, Object> values);
    int insertCode(Map<String, Object> values);
    int updateCode(Map<String, Object> values);
    int insertHistory(Map<String, Object> values);
    List<CodeHistoryRow> selectHistory(
        @Param("group") String group, @Param("size") int size, @Param("skip") long skip);
    long countHistory(@Param("group") String group);
}
