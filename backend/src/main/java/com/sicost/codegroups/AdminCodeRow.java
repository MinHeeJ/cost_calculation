package com.sicost.codegroups;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 기존 공통코드 테이블의 명시적 XML 조회 투영이다. */
public class AdminCodeRow {
    private String cdGrpId;
    private String dtlCd;
    private String cdNm;
    private String attr1Val;
    private String attr2Val;
    private String attr3Val;
    private Integer sortSeq;
    private String useYn;
    private LocalDate aplyBgnDt;

    @JsonProperty("cd_grp_id")
    public String getCdGrpId() {
        return cdGrpId;
    }

    public void setCdGrpId(String value) {
        this.cdGrpId = value;
    }

    @JsonProperty("dtl_cd")
    public String getDtlCd() {
        return dtlCd;
    }

    public void setDtlCd(String value) {
        this.dtlCd = value;
    }

    @JsonProperty("cd_nm")
    public String getCdNm() {
        return cdNm;
    }

    public void setCdNm(String value) {
        this.cdNm = value;
    }

    @JsonProperty("attr1_val")
    public String getAttr1Val() {
        return attr1Val;
    }

    public void setAttr1Val(String value) {
        this.attr1Val = value;
    }

    @JsonProperty("attr2_val")
    public String getAttr2Val() {
        return attr2Val;
    }

    public void setAttr2Val(String value) {
        this.attr2Val = value;
    }

    @JsonProperty("attr3_val")
    public String getAttr3Val() {
        return attr3Val;
    }

    public void setAttr3Val(String value) {
        this.attr3Val = value;
    }

    @JsonProperty("sort_seq")
    public Integer getSortSeq() {
        return sortSeq;
    }

    public void setSortSeq(Integer value) {
        this.sortSeq = value;
    }

    @JsonProperty("use_yn")
    public String getUseYn() {
        return useYn;
    }

    public void setUseYn(String value) {
        this.useYn = value;
    }

    @JsonProperty("aply_bgn_dt")
    public LocalDate getAplyBgnDt() {
        return aplyBgnDt;
    }

    public void setAplyBgnDt(LocalDate value) {
        this.aplyBgnDt = value;
    }
}
