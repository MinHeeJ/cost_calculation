package com.sicost.partners;

/** Safe list projection has no raw payment account properties. */
public class PartnerListItem {
    private Long id;
    private String no;
    private String ptnrSeCd;
    private String ptnrNm;
    private String bizRegNo;
    private String useYn;
    private String ceoNm;
    private String indtyCd;
    private String chrgNm;
    private String chrgTelNo;
    private String kindName;
    private String industryName;
    private String txinvEmailAddr;
    private String paymentAccountSummary;
    private long ongoingProjectCount;
    private long assignedDeveloperCount;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public String getNo() {
        return no;
    }

    public void setNo(String value) {
        this.no = value;
    }

    public String getPtnrSeCd() {
        return ptnrSeCd;
    }

    public void setPtnrSeCd(String value) {
        this.ptnrSeCd = value;
    }

    public String getPtnrNm() {
        return ptnrNm;
    }

    public void setPtnrNm(String value) {
        this.ptnrNm = value;
    }

    public String getBizRegNo() {
        return bizRegNo;
    }

    public void setBizRegNo(String value) {
        this.bizRegNo = value;
    }

    public String getUseYn() {
        return useYn;
    }

    public void setUseYn(String value) {
        this.useYn = value;
    }

    public String getCeoNm() {
        return ceoNm;
    }

    public void setCeoNm(String value) {
        this.ceoNm = value;
    }

    public String getIndtyCd() {
        return indtyCd;
    }

    public void setIndtyCd(String value) {
        this.indtyCd = value;
    }

    public String getChrgNm() {
        return chrgNm;
    }

    public void setChrgNm(String value) {
        this.chrgNm = value;
    }

    public String getChrgTelNo() {
        return chrgTelNo;
    }

    public void setChrgTelNo(String value) {
        this.chrgTelNo = value;
    }

    public String getKindName() {
        return kindName;
    }

    public void setKindName(String value) {
        this.kindName = value;
    }

    public String getIndustryName() {
        return industryName;
    }

    public void setIndustryName(String value) {
        this.industryName = value;
    }

    public String getTxinvEmailAddr() {
        return txinvEmailAddr;
    }

    public void setTxinvEmailAddr(String value) {
        this.txinvEmailAddr = value;
    }

    public String getPaymentAccountSummary() {
        return paymentAccountSummary;
    }

    public void setPaymentAccountSummary(String value) {
        this.paymentAccountSummary = value;
    }

    public long getOngoingProjectCount() {
        return ongoingProjectCount;
    }

    public void setOngoingProjectCount(long value) {
        this.ongoingProjectCount = value;
    }

    public long getAssignedDeveloperCount() {
        return assignedDeveloperCount;
    }

    public void setAssignedDeveloperCount(long value) {
        this.assignedDeveloperCount = value;
    }
}
