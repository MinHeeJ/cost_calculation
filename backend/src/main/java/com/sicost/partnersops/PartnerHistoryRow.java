package com.sicost.partnersops;

/** Explicit persistence projection for partner read results. */
public class PartnerHistoryRow {
    private java.time.LocalDateTime chgAt;
    private String dataChgTyCd;
    private String clmnNm;
    private String bfrVal;
    private String aftVal;
    private Long chgUserId;

    public java.time.LocalDateTime getChgAt() {
        return chgAt;
    }

    public void setChgAt(java.time.LocalDateTime value) {
        this.chgAt = value;
    }

    public String getDataChgTyCd() {
        return dataChgTyCd;
    }

    public void setDataChgTyCd(String value) {
        this.dataChgTyCd = value;
    }

    public String getClmnNm() {
        return clmnNm;
    }

    public void setClmnNm(String value) {
        this.clmnNm = value;
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
}
