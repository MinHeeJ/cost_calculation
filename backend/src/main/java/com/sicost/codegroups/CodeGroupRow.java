package com.sicost.codegroups;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 기존 cmn 스키마의 명시적 XML 조회 투영이며 이 기능에만 snake_case wire를 적용한다. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CodeGroupRow {
    private String cdGrpId;
    private String cdGrpNm;
    private String cdGrpSeCd;
    private String attr1Nm;
    private String attr2Nm;
    private String attr3Nm;
    private int sortSeq;
    private String useYn;
    private long codeCount;

    public String getCdGrpId() {
        return cdGrpId;
    }

    public void setCdGrpId(String value) {
        this.cdGrpId = value;
    }

    public String getCdGrpNm() {
        return cdGrpNm;
    }

    public void setCdGrpNm(String value) {
        this.cdGrpNm = value;
    }

    public String getCdGrpSeCd() {
        return cdGrpSeCd;
    }

    public void setCdGrpSeCd(String value) {
        this.cdGrpSeCd = value;
    }

    public String getAttr1Nm() {
        return attr1Nm;
    }

    public void setAttr1Nm(String value) {
        this.attr1Nm = value;
    }

    public String getAttr2Nm() {
        return attr2Nm;
    }

    public void setAttr2Nm(String value) {
        this.attr2Nm = value;
    }

    public String getAttr3Nm() {
        return attr3Nm;
    }

    public void setAttr3Nm(String value) {
        this.attr3Nm = value;
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

    public long getCodeCount() {
        return codeCount;
    }

    public void setCodeCount(long value) {
        this.codeCount = value;
    }
}
