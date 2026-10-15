package com.sicost.codegroups;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 기존 공통코드 테이블의 명시적 XML 조회 투영이다. */
public class CodeGroupRow {
    private String cdGrpId;
    private String cdGrpNm;
    private String cdGrpSeCd;
    private String attr1Nm;
    private String attr2Nm;
    private String attr3Nm;
    private Integer sortSeq;
    private String useYn;
    private Long codeCount;

    @JsonProperty("cd_grp_id")
    public String getCdGrpId() {
        return cdGrpId;
    }

    public void setCdGrpId(String value) {
        this.cdGrpId = value;
    }

    @JsonProperty("cd_grp_nm")
    public String getCdGrpNm() {
        return cdGrpNm;
    }

    public void setCdGrpNm(String value) {
        this.cdGrpNm = value;
    }

    @JsonProperty("cd_grp_se_cd")
    public String getCdGrpSeCd() {
        return cdGrpSeCd;
    }

    public void setCdGrpSeCd(String value) {
        this.cdGrpSeCd = value;
    }

    @JsonProperty("attr1_nm")
    public String getAttr1Nm() {
        return attr1Nm;
    }

    public void setAttr1Nm(String value) {
        this.attr1Nm = value;
    }

    @JsonProperty("attr2_nm")
    public String getAttr2Nm() {
        return attr2Nm;
    }

    public void setAttr2Nm(String value) {
        this.attr2Nm = value;
    }

    @JsonProperty("attr3_nm")
    public String getAttr3Nm() {
        return attr3Nm;
    }

    public void setAttr3Nm(String value) {
        this.attr3Nm = value;
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

    @JsonProperty("code_count")
    public Long getCodeCount() {
        return codeCount;
    }

    public void setCodeCount(Long value) {
        this.codeCount = value;
    }
}
