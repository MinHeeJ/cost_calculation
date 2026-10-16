package com.sicost.excelmaps;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Excel-map projection with explicit XML materialization and local snake_case wire names. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ExcelMapRow {
    private Long mapId;
    private String excelLblNm;
    private String trnsCdGrpId;
    private String trnsCd;
    private String mapRoleCd;
    private String rmrkDesc;
    private String useYn;
    private String cdGrpNm;
    private String cdNm;

    public Long getMapId() {
        return mapId;
    }

    public void setMapId(Long value) {
        this.mapId = value;
    }

    public String getExcelLblNm() {
        return excelLblNm;
    }

    public void setExcelLblNm(String value) {
        this.excelLblNm = value;
    }

    public String getTrnsCdGrpId() {
        return trnsCdGrpId;
    }

    public void setTrnsCdGrpId(String value) {
        this.trnsCdGrpId = value;
    }

    public String getTrnsCd() {
        return trnsCd;
    }

    public void setTrnsCd(String value) {
        this.trnsCd = value;
    }

    public String getMapRoleCd() {
        return mapRoleCd;
    }

    public void setMapRoleCd(String value) {
        this.mapRoleCd = value;
    }

    public String getRmrkDesc() {
        return rmrkDesc;
    }

    public void setRmrkDesc(String value) {
        this.rmrkDesc = value;
    }

    public String getUseYn() {
        return useYn;
    }

    public void setUseYn(String value) {
        this.useYn = value;
    }

    public String getCdGrpNm() {
        return cdGrpNm;
    }

    public void setCdGrpNm(String value) {
        this.cdGrpNm = value;
    }

    public String getCdNm() {
        return cdNm;
    }

    public void setCdNm(String value) {
        this.cdNm = value;
    }
}
