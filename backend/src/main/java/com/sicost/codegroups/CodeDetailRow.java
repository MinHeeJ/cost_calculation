package com.sicost.codegroups;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 기존 cmn 스키마의 명시적 XML 조회 투영이며 이 기능에만 snake_case wire를 적용한다. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CodeDetailRow {
    private String cdGrpId;
    private String dtlCd;
    private String cdNm;
    private String attr1Val;
    private String attr2Val;
    private String attr3Val;
    private int sortSeq;
    private String useYn;
    private java.time.LocalDate aplyBgnDt;

    public String getCdGrpId() {
        return cdGrpId;
    }

    public void setCdGrpId(String value) {
        this.cdGrpId = value;
    }

    public String getDtlCd() {
        return dtlCd;
    }

    public void setDtlCd(String value) {
        this.dtlCd = value;
    }

    public String getCdNm() {
        return cdNm;
    }

    public void setCdNm(String value) {
        this.cdNm = value;
    }

    public String getAttr1Val() {
        return attr1Val;
    }

    public void setAttr1Val(String value) {
        this.attr1Val = value;
    }

    public String getAttr2Val() {
        return attr2Val;
    }

    public void setAttr2Val(String value) {
        this.attr2Val = value;
    }

    public String getAttr3Val() {
        return attr3Val;
    }

    public void setAttr3Val(String value) {
        this.attr3Val = value;
    }

    public int getSortSeq() {
        return sortSeq;
    }

    public void setSortSeq(int value) {
        this.sortSeq = value;
    }

    public String getUseYn() {
        return useYn;
    }

    public void setUseYn(String value) {
        this.useYn = value;
    }

    public java.time.LocalDate getAplyBgnDt() {
        return aplyBgnDt;
    }

    public void setAplyBgnDt(java.time.LocalDate value) {
        this.aplyBgnDt = value;
    }
}
