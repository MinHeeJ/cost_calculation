package com.sicost.partnersops;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Reads existing relations and changes only partner use_yn plus atomic audit rows. */
@Mapper
public interface PartnerActivityMapper {
    PartnerStatusRow selectPartner(@Param("id") long id);
    List<PartnerOptionRow> selectOptions(@Param("kind") String kind);
    List<PartnerAssignmentRow> selectAssignments(@Param("id") long id, @Param("today") LocalDate today);
    List<PartnerHistoryRow> selectHistory(@Param("id") long id);
    int countOngoingProjects(@Param("id") long id, @Param("today") LocalDate today);
    int countAffiliatedDevelopers(@Param("id") long id, @Param("today") LocalDate today);
    int updateUse(@Param("id") long id, @Param("useYn") String useYn, @Param("actor") Long actor);
    int insertHistory(
        @Param("id") long id,
        @Param("type") String type,
        @Param("column") String column,
        @Param("before") String before,
        @Param("after") String after,
        @Param("actor") Long actor
    );
}
