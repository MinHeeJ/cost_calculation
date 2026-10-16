package com.sicost.partners;

import com.sicost.common.Sensitive;

/** Storage projection; never serialized directly into list responses. */
public class PartnerCatalogRow {
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
    @Sensitive
    private String bankCd;
    @Sensitive
    private String acntNo;
    @Sensitive
    private String dpstrNm;
    private String bankName;

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

    public String getBankCd() {
        return bankCd;
    }

    public void setBankCd(String value) {
        this.bankCd = value;
    }

    public String getAcntNo() {
        return acntNo;
    }

    public void setAcntNo(String value) {
        this.acntNo = value;
    }

    public String getDpstrNm() {
        return dpstrNm;
    }

    public void setDpstrNm(String value) {
        this.dpstrNm = value;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String value) {
        this.bankName = value;
    }
}
