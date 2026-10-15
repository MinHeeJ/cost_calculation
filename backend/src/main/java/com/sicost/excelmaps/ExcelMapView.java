package com.sicost.excelmaps;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 관리 목록은 중지 행과 기존 대상 이름도 보존한다. */
public record ExcelMapView(
    @JsonProperty("map_id") Long mapId,
    @JsonProperty("excel_lbl_nm") String excelLblNm,
    @JsonProperty("trns_cd_grp_id") String trnsCdGrpId,
    @JsonProperty("trns_cd") String trnsCd,
    @JsonProperty("map_role_cd") String mapRoleCd,
    @JsonProperty("rmrk_desc") String rmrkDesc,
    @JsonProperty("use_yn") String useYn,
    @JsonProperty("cd_grp_nm") String cdGrpNm,
    @JsonProperty("cd_nm") String cdNm
) {
    public static ExcelMapView from(ExcelMapRow row) {
        return new ExcelMapView(
            row.getMapId(),
            row.getExcelLblNm(),
            row.getTrnsCdGrpId(),
            row.getTrnsCd(),
            row.getMapRoleCd(),
            row.getRmrkDesc(),
            row.getUseYn(),
            row.getCdGrpNm(),
            row.getCdNm()
        );
    }
}
