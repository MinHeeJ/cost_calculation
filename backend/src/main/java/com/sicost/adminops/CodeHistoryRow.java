package com.sicost.adminops;

import java.time.LocalDateTime;

/** 기존 TIMESTAMP 및 nullable 처리자를 명시 resultMap으로 받는 조회 행이다. */
public class CodeHistoryRow {
    private Long cdHistId;
    private String cdGrpId;
    private String dtlCd;
    private String cdChgTyCd;
    private String itemNm;
    private String bfrVal;
    private String aftVal;
    private Long chgUserId;
    private LocalDateTime chgAt;
    private String cdGrpNm;

    public Long getCdHistId() {
        return cdHistId;
    }

    public void setCdHistId(Long value) {
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

    public LocalDateTime getChgAt() {
        return chgAt;
    }

    public void setChgAt(LocalDateTime value) {
        this.chgAt = value;
    }

    public String getCdGrpNm() {
        return cdGrpNm;
    }

    public void setCdGrpNm(String value) {
        this.cdGrpNm = value;
    }
}
