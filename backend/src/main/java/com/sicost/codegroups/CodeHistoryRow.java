package com.sicost.codegroups;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 기존 cmn 스키마의 명시적 XML 조회 투영이며 이 기능에만 snake_case wire를 적용한다. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CodeHistoryRow {
    private long cdHistId;
    private String cdGrpId;
    private String dtlCd;
    private String cdChgTyCd;
    private String itemNm;
    private String bfrVal;
    private String aftVal;
    private Long chgUserId;
    private java.time.LocalDateTime chgAt;
    private String cdGrpNm;

    public long getCdHistId() {
        return cdHistId;
    }

    public void setCdHistId(long value) {
        this.cdHistId = value;
    }

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

    public String getCdChgTyCd() {
        return cdChgTyCd;
    }

    public void setCdChgTyCd(String value) {
        this.cdChgTyCd = value;
    }

    public String getItemNm() {
        return itemNm;
    }

    public void setItemNm(String value) {
        this.itemNm = value;
    }

    public String getBfrVal() {
        return bfrVal;
    }

    public void setBfrVal(String value) {
        this.bfrVal = value;
    }

    public String getAftVal() {
        return aftVal;
    }

    public void setAftVal(String value) {
        this.aftVal = value;
    }

    public Long getChgUserId() {
        return chgUserId;
    }

    public void setChgUserId(Long value) {
        this.chgUserId = value;
    }

    public java.time.LocalDateTime getChgAt() {
        return chgAt;
    }

    public void setChgAt(java.time.LocalDateTime value) {
        this.chgAt = value;
    }

    public String getCdGrpNm() {
        return cdGrpNm;
    }

    public void setCdGrpNm(String value) {
        this.cdGrpNm = value;
    }
}
