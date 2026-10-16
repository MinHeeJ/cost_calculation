package com.sicost.partners;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Explicit XML projections and writes against the existing partner and history tables. */
@Mapper
public interface PartnerCatalogMapper {
    PartnerCatalogRow selectById(@Param("id") long id);
    String duplicateName(@Param("bizRegNo") String bizRegNo);
    String codeUse(@Param("group") String group, @Param("code") String code);
    long nextId();
    int insert(@Param("row") PartnerCatalogRow row, @Param("actor") Long actor);
    int update(@Param("id") long id, @Param("changes") Map<String, String> changes, @Param("actor") Long actor);
    int history(@Param("id") long id, @Param("type") String type, @Param("column") String column,
        @Param("before") String before, @Param("after") String after, @Param("actor") Long actor);
    List<PartnerListItem> list(@Param("kind") String kind, @Param("useYn") String useYn,
        @Param("q") String q, @Param("today") LocalDate today, @Param("order") String order,
        @Param("direction") String direction, @Param("limit") int limit, @Param("start") long start);
    long count(@Param("kind") String kind, @Param("useYn") String useYn, @Param("q") String q);
}
